# Session memory diagrams

PlantUML sources of the diagrams on the Session Memory reference pages.
The rendered SVGs live in `antora/modules/ROOT/images/session/`; re-render them after a change with:

```
java -jar plantuml.jar -tsvg -o ../../antora/modules/ROOT/images/session *.puml
```

The class and state diagrams use the built-in `smetana` layout engine, so Graphviz is not needed.
