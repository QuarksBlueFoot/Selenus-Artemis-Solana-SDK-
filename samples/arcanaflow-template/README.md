# ArcanaFlow template

This is a minimal runnable template that shows how to wire:

- ArcanaFlow frame batching
- ALT proposal building
- per-frame planning
- v0 compilation call site

It is designed as a starting point. Replace the example instruction builder with your game program instructions.

## Run

This template is a Kotlin JVM sample. It does not ship real RPC credentials.

- open this folder in IntelliJ
- set your RPC URL and a test keypair
- run Main.kt

## What it demonstrates

- batching actions into frames
- producing deterministic ALT proposals
- composing compute + fee + frame instructions
- compiling a v0 transaction using AltToolkit via ArcanaFlowV0Compiler

