# FrameID
Android app for real-time face recognition.
# Frame ID – Face Recognition App  

Frame ID is an **Android app** built with **Java and OpenCV** that performs real-time **face detection and recognition**.  
It captures images, extracts face features, and matches them using **cosine similarity / raw pixel comparison** (no ML libraries like TensorFlow Lite).  

---

## ✨ Features
- 📸 Capture faces using the device camera  
- 🎭 Automatic face detection using Haar Cascades  
- ⚡ Real-time recognition with cosine similarity  
- 💾 Store and manage face data locally  
- 🌓 Simple UI with camera switch, capture, and recognition buttons  

---

## 🛠 Tech Stack
- **Language:** Java (Android)  
- **Libraries:** OpenCV (Haar Cascade)  
- **Approach:** Manual recognition (no deep learning model, uses raw image comparison)  

---

## 📂 Project Structure
- `MainActivity.java` → handles camera preview, capture, recognition  
- `haarcascade_frontalface_default.xml` → Haar Cascade model for detection  
- `CapturedImages/` → stored training images  
- `face_data.json` → processed face vectors & labels  

---

## 🚀 How It Works
1. Capture face images (auto-saves ~100 samples).  
2. Convert → preprocess & extract faces.  
3. Recognition → compares live frame with stored faces using cosine similarity.  

---

## 📌 Notes
- This app is for **learning and experimentation** with computer vision.  
- Not production-ready, no cloud or online storage.  

---

## 🔖 License
Currently private use only. No license added.  
