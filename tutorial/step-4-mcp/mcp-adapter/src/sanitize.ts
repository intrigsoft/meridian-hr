import type { Cheerio } from "cheerio";
import type { AnyNode } from "domhandler";

/**
 * Coarse sanitize → visible text. We strip by TAG TYPE (not position), so the
 * result survives a re-skin, and we drop everything that either carries no visible
 * text or would be a leak/injection surface:
 *   - script/style/svg  → non-content noise
 *   - form/input/select/textarea/button → control chrome + hidden CSRF/session inputs
 *   - a → link chrome (and its href, which we never want in the model's context)
 *
 * What's left is the row's human-readable text, whitespace-collapsed. Business
 * identifiers (record ids) live in attributes, not visible text, so they are pulled
 * separately and deterministically — they never depend on this fuzzy path.
 */
const STRIP = "script,style,svg,form,input,select,textarea,button,a";

export function textOf(node: Cheerio<AnyNode>): string {
  const clone = node.clone();
  clone.find(STRIP).remove();
  return clone.text().replace(/\s+/g, " ").trim();
}
