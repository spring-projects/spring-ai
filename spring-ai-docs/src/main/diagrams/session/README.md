# Session diagrams

PlantUML sources of the diagrams on the Session reference pages.
The rendered SVGs live in `antora/modules/ROOT/images/session/`; re-render them after a change with:

```
java -jar plantuml.jar -tsvg -o ../../antora/modules/ROOT/images/session *.puml
```

The class and state diagrams use the built-in `smetana` layout engine, so Graphviz is not needed.

Two pictures are drawn in [Excalidraw](https://excalidraw.com) instead, and have no file here: `session-status-state.png` on the Turn Status page and `spring-ai-session-advisors.png` on the ChatClient page.
Each PNG embeds its own scene, so it is its own source.
To change one, open the PNG in Excalidraw, edit it, and export it again over the same file with "Embed scene" ticked.
