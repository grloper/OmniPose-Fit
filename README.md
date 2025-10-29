# Pushup Counter Video

An Android app for exercise tracking with pose detection using ML Kit.

## Recent Fixes

### Pose Detection Coordinate Transformation

Fixed the horizontal flip issue where skeleton was appearing mirrored (head right, legs left).

**Current State:** 
- Removed manual rotation transformations
- ML Kit's `InputImage.fromMediaImage()` with rotationDegrees handles rotation internally
- Landmarks are returned in the coordinate space of the rotated image
- Added mirroring for front camera to match PreviewView behavior

**Testing Required:**
1. Test on actual device - the skeleton may still need adjustments
2. Check debug logs in logcat for coordinate values
3. Try both front and back cameras
4. Test in different device orientations

## Build Instructions

The app requires Java 11+ and Android SDK 24+.

```bash
./gradlew assembleDebug
```

Note: The gradle build currently has JVM heap size issues. If build fails, reduce memory in `gradle.properties`.

## Key Files Modified

- `app/src/main/java/com/grloepr/pushtrack/ui/overlay/PoseOverlay.kt` - Coordinate transformation
- `app/src/main/java/com/grloepr/pushtrack/analysis/ImageAnalyzer.kt` - Image processing
- Debug logging added to help diagnose remaining issues

## Next Steps

Once skeleton alignment is confirmed working:
1. Implement exercise counting logic
2. Add form analysis
3. Create exercise library
4. Add AI assistant for real-time feedback

