# Tugas Besar 1 IF2211 Strategi Algoritma

### Author

Kelompok 3 - TriTrieTree

- Muhammad Nafis Habibi - 13524018
- Niko Samuel Simanjuntak - 13524029
- Jingglang Galih Rinenggan - 13524095

# Battlecode 2025 Scaffold - Java

This is the Battlecode 2025 Java scaffold, our bots!
There are three main bots:

1. main_bot_antem
2. alternative_bot_1_besi2
3. alternative_bot_2_fuchsia

## Deskripsi Program
Repositori ini berisi implementasi agen cerdas (bot) untuk permainan **Battlecode 2025: Chromatic Conflict** menggunakan pendekatan **Algoritma Greedy**. Karena adanya batasan visibilitas (*vision radius*) dan batasan komputasi (*bytecode limit* per giliran), bot dirancang untuk menyelesaikan sub-masalah secara *greedy* lokal, seperti navigasi pergerakan, penentuan target serangan, dan manajemen sumber daya (cat dan *chips*).

Terdapat tiga variasi bot yang dikembangkan:
1. **Bot Utama (Antem):** Memprioritaskan penguasaan wilayah secara agresif dan reaktif (*early-game domination*). Sangat ringan secara komputasi dan berhasil membuktikan laju kemenangan (*win rate*) tertinggi selama tahap pengujian.
2. **Bot Alternatif 1 (Besi2):** Mengandalkan navigasi rintangan dengan kombinasi *BugNav* dan *FuzzyMove* dengan rotasi prioritas *seed* ID serta evaluasi penargetan serang berbasis *lowest HP*.
3. **Bot Alternatif 2 (Fuschia):** Menerapkan manajemen investasi *tower* yang *risk-averse* (sangat hati-hati menahan sumber daya) dan pembobotan *greedy* dinamis (*weighted score*) pada evaluasi serangannya.

## Requirement Program
Untuk melakukan kompilasi dan menjalankan simulasi bot pada repositori ini, diperlukan beberapa hal berikut:
* **Java Development Kit (JDK) 21** atau versi yang lebih baru (Sesuai ketentuan Battlecode 2025).
* Koneksi internet aktif saat menjalankan simulasi pertama kali (untuk mengunduh *dependencies* Gradle).
* **Battlecode 2025 Client** (Opsional, untuk memutar dan memvisualisasikan file *replay* `.bc25`).
This is the Battlecode 2025 Java scaffold, containing an `examplefuncsplayer`. Read https://play.battlecode.org/bc25java/quick_start


### Project Structure

- `README.md`
    This file.
- `build.gradle`
    The Gradle build file used to build and run players.
- `src/`
    Player source code.
- `test/`
    Player test code.
- `client/`
    Contains the client. The proper executable can be found in this folder (don't move this!)
- `build/`
    Contains compiled player code and other artifacts of the build process. Can be safely ignored.
- `matches/`
    The output folder for match files.
- `maps/`
    The default folder for custom maps.
- `gradlew`, `gradlew.bat`
    The Unix (OS X/Linux) and Windows versions, respectively, of the Gradle wrapper. These are nifty scripts that you can execute in a terminal to run the Gradle build tasks of this project. If you aren't planning to do command line development, these can be safely ignored.
- `gradle/`
    Contains files used by the Gradle wrapper scripts. Can be safely ignored.

### Useful Commands

- `./gradlew build`
    Compiles your player
- `./gradlew run`
    Runs a game with the settings in gradle.properties
- `./gradlew update`
    Update configurations for the latest version -- run this often
- `./gradlew zipForSubmit`
    Create a submittable zip file
- `./gradlew tasks`
    See what else you can do!


### Configuration 

Look at `gradle.properties` for project-wide configuration.

If you are having any problems with the default client, please report to the devs and
feel free to set the `compatibilityClient` configuration to `true` to download a different version of the client.
