package com.quizai.server.v2.ai;

import com.quizai.server.web.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Locale;
import java.util.zip.ZipInputStream;

/** Deliberately bounded text extraction. Scanned PDFs require OCR outside this MVP. */
@Component
@Profile({"v2-dev", "v2-test"})
public class DocumentExtractor {
    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    public static final int MAX_TEXT_CHARS = 100_000;
    private static final int MAX_DOCX_EXPANDED = 10 * 1024 * 1024;

    public String pasted(String text) { return normalized(text); }

    public String extract(String type, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new ApiException(400, "Document is empty");
        if (bytes.length > MAX_FILE_BYTES) throw new ApiException(413, "Document exceeds 5 MiB");
        try {
            return normalized(switch (type) {
                case "TXT" -> StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                case "DOCX" -> docx(bytes);
                case "PDF" -> pdf(bytes);
                default -> throw new ApiException(400, "Unsupported document type");
            });
        } catch (ApiException e) { throw e; }
        catch (Exception e) { throw new ApiException(400, "Document cannot be read; use an unencrypted text PDF, DOCX or UTF-8 TXT"); }
    }

    public static String inputType(String name, String mime) {
        if (name == null || name.isBlank() || name.length() > 200 || name.contains("/") || name.contains("\\")
                || name.chars().anyMatch(c -> c < 32 || c == 127) || name.contains(":")) {
            throw new ApiException(400, "Invalid file name");
        }
        String lower = name.toLowerCase(Locale.ROOT);
        String media = mime == null ? "" : mime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf") && media.equals("application/pdf")) return "PDF";
        if (lower.endsWith(".docx") && media.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) return "DOCX";
        if (lower.endsWith(".txt") && media.equals("text/plain")) return "TXT";
        throw new ApiException(400, "File extension and MIME must match PDF, DOCX or TXT");
    }

    private String pdf(byte[] bytes) throws IOException {
        if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            throw new ApiException(400, "Invalid PDF header");
        }
        try (var document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted() || !document.getCurrentAccessPermission().canExtractContent()) {
                throw new ApiException(400, "PDF text extraction is not permitted");
            }
            if (document.getNumberOfPages() > 200) throw new ApiException(413, "PDF exceeds 200 pages");
            var writer = new Writer() {
                final StringBuilder value = new StringBuilder();
                @Override public void write(char[] chars, int offset, int length) {
                    if (value.length() + length > MAX_TEXT_CHARS) throw new ApiException(413, "Extracted text exceeds limit");
                    value.append(chars, offset, length);
                }
                @Override public void flush() { }
                @Override public void close() { }
                @Override public String toString() { return value.toString(); }
            };
            new PDFTextStripper().writeText(document, writer);
            return writer.toString();
        }
    }

    private String docx(byte[] bytes) throws Exception {
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') throw new ApiException(400, "Invalid DOCX container");
        byte[] content = null;
        int total = 0, entries = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++entries > 2000) throw new ApiException(413, "DOCX contains too many entries");
                var output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = zip.read(buffer)) != -1) {
                    total += n;
                    if (total > MAX_DOCX_EXPANDED) throw new ApiException(413, "DOCX expanded size exceeds limit");
                    if (entry.getName().equals("word/document.xml")) output.write(buffer, 0, n);
                }
                if (entry.getName().equals("word/document.xml")) {
                    if (content != null) throw new ApiException(400, "Duplicate DOCX document part");
                    content = output.toByteArray();
                }
            }
        }
        if (content == null) throw new ApiException(400, "DOCX document part is missing");
        var factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        var reader = factory.newSAXParser().getXMLReader();
        reader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        reader.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        StringBuilder text = new StringBuilder();
        reader.setContentHandler(new DefaultHandler() {
            boolean inText;
            private void append(String value) {
                if (text.length() + value.length() > MAX_TEXT_CHARS) throw new ApiException(413, "Extracted text exceeds limit");
                text.append(value);
            }
            @Override public void startElement(String uri, String local, String qName, Attributes attributes) {
                if (!uri.equals("http://schemas.openxmlformats.org/wordprocessingml/2006/main")) return;
                if (local.equals("t")) inText = true;
                if (local.equals("tab")) append("\t");
                if (local.equals("br")) append("\n");
            }
            @Override public void characters(char[] chars, int offset, int length) {
                if (inText) append(new String(chars, offset, length));
            }
            @Override public void endElement(String uri, String local, String qName) {
                if (!uri.equals("http://schemas.openxmlformats.org/wordprocessingml/2006/main")) return;
                if (local.equals("t")) inText = false;
                if (local.equals("p")) append("\n");
            }
        });
        reader.setErrorHandler(new DefaultHandler() {
            @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
        });
        reader.parse(new InputSource(new ByteArrayInputStream(content)));
        return text.toString();
    }

    private static String normalized(String text) {
        if (text == null) throw new ApiException(400, "Document text is required");
        if (text.length() > MAX_TEXT_CHARS) throw new ApiException(413, "Extracted text exceeds 100000 characters");
        if (text.chars().anyMatch(c -> (c < 32 && c != '\n' && c != '\r' && c != '\t') || c == 127)) {
            throw new ApiException(400, "Document contains unsupported control characters");
        }
        String result = text.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n').strip();
        if (result.isBlank()) throw new ApiException(400, "No extractable text found; scanned PDF OCR is outside MVP");
        return result;
    }
}
