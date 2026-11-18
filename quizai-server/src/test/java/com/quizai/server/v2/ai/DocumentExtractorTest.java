package com.quizai.server.v2.ai;

import com.quizai.server.web.ApiException;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class DocumentExtractorTest {
    private final DocumentExtractor extractor = new DocumentExtractor();

    @Test void normalizesUtf8AndPastedText() {
        assertEquals("Tiếng Việt\nJava", extractor.extract("TXT", "\uFEFFTiếng Việt\r\nJava".getBytes(StandardCharsets.UTF_8)));
        assertEquals("Java\nclass", extractor.pasted("  Java\r\nclass  "));
    }

    @Test void rejectsMalformedUtf8NullBytesBlankAndOversizeInput() {
        assertEquals(400, assertThrows(ApiException.class, () -> extractor.extract("TXT", new byte[]{(byte) 0xc3, 0x28})).status());
        assertThrows(ApiException.class, () -> extractor.pasted("a\u0000b"));
        assertThrows(ApiException.class, () -> extractor.pasted("  "));
        assertEquals(413, assertThrows(ApiException.class, () -> extractor.pasted("a".repeat(DocumentExtractor.MAX_TEXT_CHARS + 1))).status());
    }

    @Test void extractsDocxParagraphsWithoutExternalEntities() throws Exception {
        byte[] docx = docx("<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:body><w:p><w:r><w:t>Java</w:t></w:r></w:p><w:p><w:r><w:t>class</w:t></w:r></w:p></w:body></w:document>");
        assertEquals("Java\nclass", extractor.extract("DOCX", docx));
        assertThrows(ApiException.class, () -> extractor.extract("DOCX", docx("<!DOCTYPE a [<!ENTITY x SYSTEM 'file:///c:/windows/win.ini'>]><w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:p><w:r><w:t>&x;</w:t></w:r></w:p></w:document>")));
    }

    @Test void rejectsSpoofedFilesAndUnsafeNames() {
        assertThrows(ApiException.class, () -> extractor.extract("PDF", "not pdf".getBytes(StandardCharsets.UTF_8)));
        assertThrows(ApiException.class, () -> extractor.extract("DOCX", "not zip".getBytes(StandardCharsets.UTF_8)));
        assertThrows(ApiException.class, () -> DocumentExtractor.inputType("../private.pdf", "application/pdf"));
        assertThrows(ApiException.class, () -> DocumentExtractor.inputType("a.pdf", "text/plain"));
        assertEquals("TXT", DocumentExtractor.inputType("notes.TXT", "text/plain"));
    }

    @Test void extractsTextPdfAndRejectsPdfWithoutText() throws Exception {
        try (var pdf = new PDDocument()) {
            var page = new PDPage(); pdf.addPage(page);
            try (var content = new PDPageContentStream(pdf, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("Java class");
                content.endText();
            }
            var bytes = new ByteArrayOutputStream(); pdf.save(bytes);
            assertEquals("Java class", extractor.extract("PDF", bytes.toByteArray()));
        }
        try (var pdf = new PDDocument()) {
            pdf.addPage(new PDPage());
            var bytes = new ByteArrayOutputStream(); pdf.save(bytes);
            assertEquals(400, assertThrows(ApiException.class, () -> extractor.extract("PDF", bytes.toByteArray())).status());
        }
    }

    private static byte[] docx(String xml) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
