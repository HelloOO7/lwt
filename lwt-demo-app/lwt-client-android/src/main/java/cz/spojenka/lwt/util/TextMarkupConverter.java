package cz.spojenka.lwt.util;

import android.graphics.Typeface;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.TypefaceSpan;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class TextMarkupConverter {

    private static final String TAG = "TextMarkupConverter";

    private static final String ICON_FONT_FAMILY = "__ICONFONT";
    private final Typeface iconFont;

    public TextMarkupConverter(Typeface iconFont) {
        this.iconFont = iconFont;
    }

    public Spanned toSpannableString(String markup) {
        String html = toHtml(markup);
        Spanned parsedHtml = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT);
        return injectIconTypeface(parsedHtml);
    }

    public BackgroundColorSpan extractBackgroundColor(Spanned spanned) {
        BackgroundColorSpan[] bgSpans = spanned.getSpans(0, spanned.length(), BackgroundColorSpan.class);
        if (bgSpans.length > 0) {
            return bgSpans[0];
        }
        return null;
    }

    private Spanned injectIconTypeface(Spanned source) {
        SpannableStringBuilder builder = new SpannableStringBuilder(source);
        for (TypefaceSpan tfs : builder.getSpans(0, builder.length(), TypefaceSpan.class)) {
            if (ICON_FONT_FAMILY.equals(tfs.getFamily())) {
                int start = builder.getSpanStart(tfs);
                int end = builder.getSpanEnd(tfs);
                builder.removeSpan(tfs);
                builder.setSpan(new TypefaceSpan(iconFont), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return builder;
    }

    private String toHtml(String markup) {
        try {
            Document doc = parseMarkup(markup);
            return toHtml(doc, doc.getDocumentElement());
        } catch (ParserConfigurationException | SAXException | IOException e) {
            Log.e(TAG, "Failed to parse markup: " + markup, e);
            return markup;
        }
    }

    private String toHtml(Document document, Node node) {
        if (node instanceof Element element) {
            Node transformedNode = transformElement(document, element);
            if (transformedNode != null) {
                return toHtml(document, transformedNode);
            } else {
                boolean isRoot = "root".equals(element.getTagName());
                StringBuilder htmlBuilder = new StringBuilder();
                if (!isRoot) {
                    htmlBuilder.append("<").append(element.getTagName());
                    for (int i = 0; i < element.getAttributes().getLength(); i++) {
                        Node attr = element.getAttributes().item(i);
                        htmlBuilder.append(" ")
                                .append(attr.getNodeName())
                                .append("=\"")
                                .append(Html.escapeHtml(attr.getNodeValue()))
                                .append("\"");
                    }
                    htmlBuilder.append(">");
                }
                for (int i = 0; i < element.getChildNodes().getLength(); i++) {
                    Node childNode = element.getChildNodes().item(i);
                    htmlBuilder.append(toHtml(document, childNode));
                }
                if (!isRoot) {
                    htmlBuilder.append("</").append(element.getTagName()).append(">");
                }
                return htmlBuilder.toString();
            }
        } else if (node instanceof Text) {
            return Html.escapeHtml(node.getTextContent());
        } else {
            return "";
        }
    }

    private Node transformElement(Document document, Element element) {
        if ("color".equals(element.getTagName())) {
            Element span = document.createElement("span");
            String bg = element.getAttribute("bg");
            String style = "";
            // there must be a gap (" ") before the styles because Html.fromHtml in Android
            // parses this using a regex that has a + instead of a *.
            if (bg != null) {
                style += " background-color: " + bg + ";";
            }
            String fg = element.getAttribute("fg");
            if (fg != null) {
                style += " color: " + fg + ";";
            }
            if (!style.isEmpty()) {
                span.setAttribute("style", style);
            }
            moveChildNodes(element, span);
            return span;
        } else if ("icon".equals(element.getTagName())) {
            String type = element.getAttribute("type");
            if (type != null) {
                String charCode = PIDIconFont.getCharCodeForIcon(type);
                if (!charCode.isEmpty()) {
                    Element font = document.createElement("font");
                    font.setAttribute("face", ICON_FONT_FAMILY);
                    if (!PIDIconFont.isIconTintable(type)) {
                        // set color to white to prevent Android's setting of font colors
                        font.setAttribute("color", "#ffffff");
                    }
                    font.setTextContent(" " + charCode + " ");
                    return font;
                }
            }
        }
        return null;
    }

    private static void moveChildNodes(Element from, Element to) {
        while (from.hasChildNodes()) {
            Node child = from.getFirstChild();
            from.removeChild(child);
            to.appendChild(child);
        }
    }

    private static Document parseMarkup(String markup) throws ParserConfigurationException, IOException, SAXException {
        return DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new InputSource(new StringReader("<root>" + markup + "</root>")));
    }
}
