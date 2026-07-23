# Wiring the assistant rail into the app shell

Two edits mount the rail (both reverted on the bare `main` branch):

**`LayoutAdvice.java`** — inject `DioscProperties` and expose it to every view:
```java
private final DioscProperties diosc;                    // constructor field
// …in the constructor: this.diosc = diosc;
model.addAttribute("diosc", diosc);                     // in shell(Model)
```

**`templates/layout.html`** — restore the collapse state before first paint, and
mount the rail fragment at the end of the shell:
```html
<script>(function () { try { if (localStorage.getItem('meridian.rail') === 'collapsed') document.documentElement.classList.add('rail-collapsed'); } catch (e) {} })();</script>
...
<th:block th:replace="~{fragments/assistant-rail :: rail}"></th:block>
```
