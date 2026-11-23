# SMS Guard 

**An Android application for SMS spam and fraud detection using Natural Language Processing and Machine Learning.**

##  Overview

SMS Guard is an Android application designed to analyze incoming SMS messages and identify potentially suspicious messages, including spam and fraudulent content.

The application integrates machine learning models directly into Android, allowing SMS analysis on the device.

##  Features

- **SMS Classification:** Detects normal and suspicious SMS messages.
- **Automatic Analysis:** Analyzes incoming SMS messages.
- **Manual Analysis:** Allows users to submit a message for analysis.
- **Notifications:** Notifies users when a suspicious SMS is detected.
- **SMS History:** Stores analyzed messages and their classification results.
- **On-device Inference:** Runs machine learning models locally using ONNX Runtime.

##  Machine Learning Approach

SMS Guard uses an NLP-based classification pipeline:

1. Text preprocessing and normalization.
2. Tokenization using a Hugging Face tokenizer.
3. Text embedding generation using a Sentence Transformer.
4. Classification using a Support Vector Machine (SVM).
5. Display of the prediction in the Android application.

### Models

- **Sentence Encoder:** `paraphrase-multilingual-MiniLM-L12-v2`
- **Classifier:** Support Vector Machine (SVM)
- **Model Format:** ONNX

##  Technologies Used

- Kotlin
- Android Studio
- Jetpack Compose
- Material 3
- Python (model development)
- Natural Language Processing (NLP)
- Machine Learning
- Hugging Face Tokenizers
- ONNX Runtime
- Git and GitHub
- Git LFS

##  Project Structure

```text
SMS-Guard/
├── app/
│   └── src/
│       └── main/
│           ├── assets/
│           │   ├── embedding_config.json
│           │   ├── sentence_encoder_int8.onnx
│           │   ├── sms_emb_svm.onnx
│           │   └── tokenizer/
│           ├── java/com/smsguard/app/
│           │   ├── MainActivity.kt
│           │   ├── SmsReceiver.kt
│           │   ├── SmsClassifierEngine.kt
│           │   ├── OnnxSmsClassifier.kt
│           │   └── HuggingFaceTokenizerHelper.kt
│           └── jniLibs/
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

##  Installation

1. Clone the repository:

```bash
git clone https://github.com/siham200/SMS-Guard.git
```

2. Open the project in Android Studio.

3. Wait for Gradle synchronization.

4. Connect an Android device or start an emulator.

5. Build and run the application.

6. Grant the required SMS permissions to enable automatic SMS analysis.

##  Privacy

SMS Guard performs machine learning inference locally on the Android device. SMS content is processed by the application for classification.

## Author

**Siham EL KADDOURI **
** Atimad BEL CAID **
GitHub: [@siham200](https://github.com/siham200)

---

*SMS Guard — Helping users identify suspicious SMS messages.*
