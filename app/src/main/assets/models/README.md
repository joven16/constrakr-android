# ML model assets

Export from iOS Core ML packages in the ConsTrakr iOS repo:

| iOS | Android asset |
|-----|---------------|
| `FaceRecognition/Models/AdaFace_IR18.mlpackage` | `adaface_ir18.tflite` |
| `FaceRecognition/Models/MiniFASNetV2.mlpackage` | `minifasnetv2.tflite` |

**Do not commit placeholder weights.** Place exported `.tflite` files in this folder locally.

AdaFace: 112×112 input, 512-d L2-normalized output.  
MiniFASNetV2: 80×80 BGR NCHW, 2-class logits (fake/live).

See `ConsTrakr/docs/IOS_ANDROID_PARITY.md` for preprocessing parity with iOS.
