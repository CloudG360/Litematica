Litematica 1.12.2: With Printer
==============
Litematica is a client-side Minecraft mod using LiteLoader.
It is more or less a re-creation of or a substitute for [Schematica](https://minecraft.curseforge.com/projects/schematica),
for players who don't want to have Forge installed.
For compiled builds (= downloads), see http://minecraft.curseforge.com/projects/litematica

Changes to this fork
====================

This fork takes the Schematic Printer implementation from Lunatrius's Schematica mod and tries to make it work with Litematica.

I was working on this fork to get the printer on a forge 1.12.2 server, however, the modpack was not compatible with the existing Litematica build and kept on causing rendering-based crashes which I'm not experienced enough in Forge to fix This project is now on hold, due to this.

Making the Schematica classes work within Litematica is complete (Along with most of the configuration options needed), if anyone wants to integrate it with the GUI, feel free to make a fork or submit a PR. Look into the **SchematicPrinter** class.

Compiling
=========
* Clone the repository
* Open a command prompt/terminal to the repository directory
* run 'gradlew setupDecompWorkspace'
* run 'gradlew build'
* The built jar file will be in build/libs/
