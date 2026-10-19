# Session diagrams

PlantUML sources of the diagrams on the Session reference pages.
The rendered SVGs live in `antora/modules/ROOT/images/session/`; re-render them after a change with:

```
java -jar plantuml.jar -tsvg -o ../../antora/modules/ROOT/images/session *.puml
```

The class and state diagrams use the built-in `smetana` layout engine, so Graphviz is not needed.

`spring-ai-session-advisors.svg` is the hand-made source of the advisor nesting picture, `spring-ai-session-advisors.png` on the ChatClient page; render it with a browser at 1200 x 700, headless Chrome for example:

```
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --hide-scrollbars --window-size=1200,700 \
  --screenshot=../../antora/modules/ROOT/images/session/spring-ai-session-advisors.png "file://$PWD/spring-ai-session-advisors.svg"
```
