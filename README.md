# 📦 ChestSeparators

![Java](https://img.shields.io/badge/Language-Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Framework](https://img.shields.io/badge/Framework-Fabric_MC-blue?style=for-the-badge)
![Status](https://img.shields.io/badge/Status-Active_Development-green?style=for-the-badge)

**ChestSeparators** is a non-intrusive UI overlay mod designed to enhance inventory management UX without altering core game mechanics.

> 🚧 **Work in Progress:** This project is currently under active development. The architecture focuses on leveraging Mixins for GUI injection and implementing a robust client-server packet system for data synchronization.

## 🎯 Technical Goals
* **Purely Visual Layer:** Implementation of a custom rendering pipeline to draw separators over standard container interfaces.
* **Vanilla-Faithful:** No new physical items or blocks. The logic relies entirely on existing GUI manipulation.
* **State Persistence:** Unique container identification (Server-side) to store visual layouts per chest.
* **Seamless Integration:** Zero interference with game logic (hoppers, comparators, or redstone mechanics remain untouched).

## 🛠️ Tech Stack
* **Language:** Java
* **Loader:** Fabric Loader
* **Core Concepts:** Mixins (ASM), Networking (S2C Packets), NBT Data Storage, OpenGL (Rendering).

---
*Stay tuned for updates as features are implemented commit by commit.*