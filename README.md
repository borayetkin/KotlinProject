# File Explorer Android App

A simple Android app built with Kotlin that demonstrates:

- **Navigation Drawer** with menu items
- **RecyclerView** for listing files
- **Internal Storage** file access
- **Material Design** UI

## 🚀 How It Works

1. **Navigation Drawer**: Slide-out menu with Home, File List, and Gallery sections
2. **File Explorer**: Shows files from the app's internal storage directory
3. **Sample Files**: Automatically creates sample files if none exist
4. **File Details**: Displays file name, size, modification date, and type (User/System)

## 📱 Features

- 📁 Lists files from internal storage (`/data/user/0/com.example.myapplication/files/`)
- 🎨 Card-based layout with Material Design
- 📊 Human-readable file sizes (B, KB, MB, GB)
- 🤖 Distinguishes between System files (created by Android) and User files (created by app)
- 📅 Shows last modification date
- ✨ Auto-creates sample files for demonstration

## 🏗️ Architecture

- **MainActivity**: Hosts the navigation drawer and manages fragments
- **FileListFragment**: Contains the RecyclerView for file listing
- **FileAdapter**: RecyclerView adapter that displays individual files
- **No external permissions required** - uses internal storage only

## 🛠️ Setup

1. Open in Android Studio
2. Sync Gradle dependencies
3. Run on device or emulator
4. Use hamburger menu → "File List" to view files

## 📂 File Structure

```
app/src/main/java/com/example/myapplication/
├── MainActivity.kt          # Navigation drawer setup
├── FileListFragment.kt      # File listing logic
├── FileAdapter.kt          # RecyclerView adapter
├── HomeFragment.kt         # Home page
└── GalleryFragment.kt      # Gallery page
```

## 🎯 Key Dependencies

- `androidx.appcompat` - AppCompat support
- `material` - Material Design components
- `recyclerview` - List display
- `cardview` - Card-based layout
- `fragment-ktx` - Fragment management

---

**Note**: The app uses internal storage only, so no file permissions are needed. Sample files are created automatically for demonstration purposes.
