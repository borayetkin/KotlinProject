# 🎬 Test Audio Setup for Emulator Testing

This directory contains pre-recorded audio files for testing the voice recorder app on emulator where microphone access isn't available.

## 📁 Required Files

Place your Turkish speech audio files here with these exact names:

- `test_audio_1.wav`
- `test_audio_2.wav`
- `test_audio_3.wav`

## 🎵 Audio File Specifications

Your audio files must have these specifications to work properly:

- **Format**: WAV (PCM)
- **Sample Rate**: 16,000 Hz (16kHz)
- **Channels**: Mono (1 channel)
- **Bit Depth**: 16-bit
- **Duration**: 3-10 seconds of Turkish speech
- **Content**: Clear Turkish speech for STT testing

## 🔧 How to Convert Audio Files

### Using FFmpeg (Recommended)

```bash
ffmpeg -i your_audio.mp3 -ar 16000 -ac 1 -f wav test_audio_1.wav
```

### Using Audacity (Free Software)

1. Open your audio file in Audacity
2. Go to **Tracks** → **Resample** → Set to **16000 Hz**
3. Go to **Tracks** → **Mix** → **Mix and Render** (if stereo)
4. Go to **File** → **Export** → **Export as WAV**
5. Choose **16-bit PCM** format

## 🎯 Enable Test Mode

1. Open `TestAudioProvider.kt`
2. Change: `const val ENABLE_TEST_MODE = false` to `const val ENABLE_TEST_MODE = true`
3. Run the app

## 🎪 How It Works

When test mode is enabled:

1. App starts in listening mode
2. After 3 seconds, it simulates "speech detection"
3. Loads one of your test audio files
4. Processes it through STT and translation
5. Creates WAV and transcript files
6. Repeats every 8 seconds with the next test file

## 💡 Sample Turkish Phrases

Good test phrases to record:

- "Merhaba, nasılsın?" (Hello, how are you?)
- "Bu bir test kaydıdır" (This is a test recording)
- "Türkçe konuşma tanıma testi" (Turkish speech recognition test)
- "Bugün hava çok güzel" (The weather is very nice today)

## 🔍 Troubleshooting

**❌ "No test audio files found"**

- Check file names exactly match: `test_audio_1.wav`, etc.
- Ensure files are in `app/src/main/assets/test_audio/`
- Verify audio format is correct (16kHz, mono, 16-bit WAV)

**❌ "Failed to load test audio"**

- File might be corrupted or wrong format
- Try re-converting with FFmpeg
- Check file is not empty

**❌ STT not working**

- Ensure your Turkish speech is clear
- Check if Vosk model is properly loaded
- Test with different speech samples

## 🚀 Ready to Test!

Once you've added your test files and enabled test mode:

1. Launch the app on emulator
2. Go to Voice Recorder screen
3. Tap the microphone button
4. Watch the magic happen! 🎭

The app will automatically cycle through your test files and process them through the complete pipeline: recording → STT → translation → file creation.
