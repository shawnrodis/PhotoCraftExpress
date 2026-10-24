package com.shawnrodis.photocraftexpress.ui

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.shawnrodis.photocraftexpress.engine.CropRect
import com.shawnrodis.photocraftexpress.engine.EditState
import com.shawnrodis.photocraftexpress.engine.FaceRenderData
import com.shawnrodis.photocraftexpress.engine.GeometryMap
import com.shawnrodis.photocraftexpress.engine.PixelImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device ML Kit analysis: face detection (Face / Makeup / Eyes tools)
 * and selfie segmentation (Background tool, people-only). Runs on a
 * downscaled copy of the photo for speed; all coordinates are normalized
 * to the source frame so they survive preview/export resolution changes.
 * Nothing leaves the device.
 */
object FaceAnalysis {

    /** One detected face, normalized to the source frame. */
    data class DetectedFace(
        val rect: CropRect,
        /** Keys: leftEye, rightEye, nose, mouth, leftCheek, rightCheek. */
        val landmarks: Map<String, Pair<Float, Float>>,
        /** Face width as a fraction of source image width. */
        val faceWidth: Float
    )

    data class Result(
        val faces: List<DetectedFace>,
        /** Person mask in source frame (R = confidence 0..255), or null. */
        val personMask: PixelImage?,
        val personFound: Boolean
    )

    private val faceClient by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        FaceDetection.getClient(options)
    }

    private val segmenter by lazy {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        Segmentation.getClient(options)
    }

    /**
     * Analyzes [bitmap] (any size; internally downscaled to at most 640
     * on the long edge). Safe to call from a coroutine; ML Kit work runs
     * off the main thread. Failures yield an empty result, never a crash.
     */
    suspend fun analyze(bitmap: Bitmap): Result = withContext(Dispatchers.Default) {
        val input = if (maxOf(bitmap.width, bitmap.height) > 640) {
            val scale = 640f / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                maxOf(1, (bitmap.width * scale).toInt()),
                maxOf(1, (bitmap.height * scale).toInt()),
                true
            )
        } else {
            bitmap
        }
        val w = input.width.toFloat()
        val h = input.height.toFloat()
        val faces = try {
            val found = Tasks.await(faceClient.process(InputImage.fromBitmap(input, 0)))
            found.mapNotNull { face ->
                val box = face.boundingBox
                if (box.width() <= 0 || box.height() <= 0) return@mapNotNull null
                val rect = CropRect(
                    (box.left / w).coerceIn(0f, 1f),
                    (box.top / h).coerceIn(0f, 1f),
                    (box.right / w).coerceIn(0f, 1f),
                    (box.bottom / h).coerceIn(0f, 1f)
                )
                fun lm(type: Int): Pair<Float, Float>? {
                    val p = face.getLandmark(type)?.position ?: return null
                    return (p.x / w) to (p.y / h)
                }
                val lmMap = mutableMapOf<String, Pair<Float, Float>>()
                lm(FaceLandmark.LEFT_EYE)?.let { lmMap["leftEye"] = it }
                lm(FaceLandmark.RIGHT_EYE)?.let { lmMap["rightEye"] = it }
                lm(FaceLandmark.NOSE_BASE)?.let { lmMap["nose"] = it }
                lm(FaceLandmark.LEFT_CHEEK)?.let { lmMap["leftCheek"] = it }
                lm(FaceLandmark.RIGHT_CHEEK)?.let { lmMap["rightCheek"] = it }
                val mouthLeft = lm(FaceLandmark.MOUTH_LEFT)
                val mouthRight = lm(FaceLandmark.MOUTH_RIGHT)
                if (mouthLeft != null && mouthRight != null) {
                    lmMap["mouth"] = (
                        (mouthLeft.first + mouthRight.first) / 2f
                        ) to ((mouthLeft.second + mouthRight.second) / 2f)
                } else {
                    lm(FaceLandmark.MOUTH_BOTTOM)?.let { lmMap["mouth"] = it }
                }
                DetectedFace(rect, lmMap, box.width() / w)
            }
        } catch (e: Exception) {
            emptyList()
        }
        var personMask: PixelImage? = null
        var personFound = false
        try {
            val mask = Tasks.await(segmenter.process(InputImage.fromBitmap(input, 0)))
            val mw = mask.width
            val mh = mask.height
            val buf = mask.buffer
            buf.rewind()
            val px = IntArray(mw * mh)
            var maxConf = 0f
            for (i in px.indices) {
                val conf = if (buf.remaining() >= 4) buf.float else 0f
                if (conf > maxConf) maxConf = conf
                val v = (conf.coerceIn(0f, 1f) * 255f).toInt()
                px[i] = -0x1000000 or (v shl 16) or (v shl 8) or v
            }
            personMask = PixelImage(px, mw, mh)
            personFound = maxConf > 0.5f
        } catch (e: Exception) {
            personMask = null
        }
        Result(faces, personMask, personFound)
    }

    /**
     * Maps one detected face into the rendered frame, following the same
     * geometry the pipeline applies. [applyCrop] must match the flag the
     * render call uses (false while the Crop tool previews uncropped).
     * [srcAspect] is source width/height (for the face-width fraction when
     * the frame is rotated 90/270°).
     */
    fun mapFace(
        face: DetectedFace,
        state: EditState,
        applyCrop: Boolean,
        srcAspect: Float
    ): FaceRenderData {
        val crop = if (applyCrop) state.crop else CropRect.FULL
        fun mapPt(x: Float, y: Float) =
            GeometryMap.mapPointFull(x, y, state.rotation, state.flipH, state.flipV, crop, state.transform)
        val corners = listOf(
            mapPt(face.rect.left, face.rect.top),
            mapPt(face.rect.right, face.rect.top),
            mapPt(face.rect.right, face.rect.bottom),
            mapPt(face.rect.left, face.rect.bottom)
        )
        val xs = corners.map { it.first }
        val ys = corners.map { it.second }
        val mappedRect = CropRect(
            xs.minOrNull() ?: 0f, ys.minOrNull() ?: 0f,
            xs.maxOrNull() ?: 1f, ys.maxOrNull() ?: 1f
        )
        val mappedLandmarks = face.landmarks.mapValues { (_, p) -> mapPt(p.first, p.second) }
        val rotated = state.rotation == 90 || state.rotation == 270
        val renderedFaceWidth = if (rotated) face.faceWidth * srcAspect else face.faceWidth
        return FaceRenderData(mappedRect, mappedLandmarks, renderedFaceWidth)
    }
}
