import React, { useEffect, useRef } from "react";
import { Button } from "./ui";

// Only text and a small fixed set of formatting nodes are constructed. No HTML
// from notes, clipboard or external services is inserted into the DOM.
function inline(parent: HTMLElement, text: string) {
  const pattern = /\*\*(.+?)\*\*|`([^`]+)`|\*([^*]+)\*/g;
  let cursor = 0;
  for (const match of text.matchAll(pattern)) {
    parent.append(document.createTextNode(text.slice(cursor, match.index)));
    const node = document.createElement(
      match[1] ? "strong" : match[2] ? "code" : "em",
    );
    node.textContent = match[1] || match[2] || match[3];
    parent.append(node);
    cursor = match.index! + match[0].length;
  }
  parent.append(document.createTextNode(text.slice(cursor)));
}
function renderMarkdown(element: HTMLElement, markdown: string) {
  element.replaceChildren();
  let pre: HTMLElement | null = null;
  for (const line of markdown.split("\n")) {
    if (line.startsWith("```")) {
      if (pre) pre = null;
      else {
        pre = document.createElement("pre");
        element.append(pre);
      }
      continue;
    }
    if (pre) {
      pre.append(document.createTextNode((pre.textContent ? "\n" : "") + line));
      continue;
    }
    const heading = line.match(/^(#{1,3})\s+(.+)/),
      list = line.match(/^[-*]\s+(.+)/),
      quote = line.match(/^>\s*(.*)/);
    const node = document.createElement(
      heading
        ? "h" + heading[1].length
        : list
          ? "li"
          : quote
            ? "blockquote"
            : "p",
    );
    inline(
      node,
      heading ? heading[2] : list ? list[1] : quote ? quote[1] : line,
    );
    if (!line) node.append(document.createElement("br"));
    element.append(node);
  }
}
function markdown(node: Node): string {
  if (node.nodeType === Node.TEXT_NODE) return node.textContent || "";
  if (!(node instanceof HTMLElement)) return "";
  const content = Array.from(node.childNodes).map(markdown).join("");
  switch (node.tagName) {
    case "STRONG":
    case "B":
      return "**" + content + "**";
    case "EM":
    case "I":
      return "*" + content + "*";
    case "CODE":
      return "`" + content + "`";
    case "BR":
      return "\n";
    case "H1":
    case "H2":
    case "H3":
      return "#".repeat(Number(node.tagName[1])) + " " + content.trim() + "\n";
    case "LI":
      return "- " + content.trim() + "\n";
    case "BLOCKQUOTE":
      return "> " + content.trim() + "\n";
    case "PRE":
      return "```\n" + (node.textContent || "") + "\n```\n";
    case "P":
    case "DIV":
      return content.replace(/\n$/, "") + "\n";
    default:
      return content;
  }
}
export default function RichEditor({
  initial,
  onChange,
}: {
  initial: string;
  onChange: (text: string) => void;
}) {
  const ref = useRef<HTMLDivElement>(null),
    initialText = useRef(initial),
    change = useRef(onChange);
  change.current = onChange;
  useEffect(() => {
    if (ref.current) renderMarkdown(ref.current, initialText.current);
  }, []);
  const emit = () => {
    if (ref.current)
      change.current(
        Array.from(ref.current.childNodes)
          .map(markdown)
          .join("")
          .replace(/\n$/, ""),
      );
  };
  const format = (tag: string) => {
    const selection = window.getSelection();
    if (!selection?.rangeCount || !ref.current?.contains(selection.anchorNode))
      return;
    const range = selection.getRangeAt(0),
      node = document.createElement(tag);
    node.append(range.extractContents());
    if (!node.textContent)
      node.append(
        document.createTextNode(
          tag === "strong" ? "Texto em destaque" : "Texto",
        ),
      );
    range.insertNode(node);
    range.selectNodeContents(node);
    selection.removeAllRanges();
    selection.addRange(range);
    emit();
  };
  return (
    <>
      <div className="note-format-toolbar">
        {[
          ["strong", "Negrito"],
          ["em", "Itálico"],
          ["h2", "Título"],
          ["blockquote", "Citação"],
          ["code", "Código"],
        ].map(([tag, label]) => (
          <Button key={tag} onClick={() => format(tag)}>
            {label}
          </Button>
        ))}
      </div>
      <div
        ref={ref}
        className="rich-note"
        contentEditable
        role="textbox"
        aria-label="Editor visual da nota"
        aria-multiline
        suppressContentEditableWarning
        onInput={emit}
        onBlur={emit}
        onPaste={(e) => {
          e.preventDefault();
          const text = e.clipboardData.getData("text/plain").slice(0, 100000),
            selection = window.getSelection();
          if (!selection?.rangeCount) return;
          const range = selection.getRangeAt(0);
          if (!ref.current?.contains(range.commonAncestorContainer)) return;
          range.deleteContents();
          const node = document.createTextNode(text);
          range.insertNode(node);
          range.setStartAfter(node);
          range.collapse(true);
          selection.removeAllRanges();
          selection.addRange(range);
          emit();
        }}
      />
    </>
  );
}
