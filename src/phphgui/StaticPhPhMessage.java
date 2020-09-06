
package phphgui;

import java.io.*;
import static java.lang.Float.parseFloat;
import static java.lang.Integer.parseInt;
import static java.lang.Math.round;
import static java.lang.System.out;
import java.net.URI;
import java.net.URISyntaxException;
import static java.net.URLDecoder.decode;
import static java.nio.file.Files.probeContentType;
import java.text.SimpleDateFormat;
import java.util.*;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import java.util.regex.*;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.DOTALL;
import static java.util.regex.Pattern.compile;
import javax.activation.FileTypeMap;
import javax.mail.*;
import javax.mail.internet.*;
import org.apache.tika.language.detect.*;
import static phphgui.PhPhCSVWriter.phPhWriter;

/**
 * Author: Malachi Woodlee
 *
 * Date: 12/8/2019
 *
 * Purpose: Utility that parses an email and counts the number of predefined features that are found
 **/

public class StaticPhPhMessage extends MimeMessage {

    //class variables
    private final MimeMessage message;
    private MimeMultipart multipart;
    private String str, subject, contentType, body, methodName, subString;
    HashMap<String, Integer> messageMap;
    private int value = 0, length = 0, mimePartsTotal = 0, mixed = 0,
            inline = 0, attach = 0, multi = 0, sentence = 0, frames = 0;
    static int notRecognized = 0;
    byte[] fileData;
    private int byteSize;
    private boolean isRecognized, versionExists, checkReply, checkSubject;
    private Date sentDate, receivedDate;
    PhPhCSVWriter csvWriter;
    private ByteArrayOutputStream bytes;
    private final ArrayList<String> contentTypeList = new ArrayList<>();
    private final ArrayList<String> boundaryFields = new ArrayList<>();
    private final ArrayList<String> cte = new ArrayList<>();
    private final ArrayList<String> charsetValues = new ArrayList<>();
    private final ArrayList<String> links = new ArrayList<>();
    private final ArrayList<String> unkwnTypes = new ArrayList<>();

    //HashSet doesn't allow duplicates, if multiple of same type is found, only one is counted
    private final HashSet uniqueDisp = new HashSet();
    private final HashSet uniqueType = new HashSet();
    private final HashSet uniqueBody;
    private final HashSet uniqueChar = new HashSet();
    private final HashSet uniqueEncoding = new HashSet();
    private final HashSet uniqueDomains = new HashSet();
    private final HashSet uniqueEmlAddDomains = new HashSet();

    //arrays for file extensions
    private final String[] audioEx = {".asnd", ".wav", ".aiff", ".aif", ".mp3", ".m4a",
        ".wma", ".sd2", ".aac"};
    private final String[] textEx = {".txt", ".xml", ".csv"};
    private final String[] videoEx = {".webm", ".mpg", ".mp2", ".mpeg", ".mpe", ".mpv",
        ".mp4", ".m4p", ".m4v", ".avi", ".wmv", ".mov", ".qt",
        ".flv", ".swf"};
    private final String[] imageEx = {".jpg", ".jpeg", ".jpe", ".jif", ".jfif", ".jfi",
        ".png", ".gif", ".webp", ".tif", ".tiff", ".psd", ".raw", ".arw",
        ".cr2", ".nrw", ".k25", ".bmp", ".dib", ".heif", ".heic", ".ind",
        ".indd", ".indt", ".jp2", ".j2k", ".jpf", ".jpx", ".jpm", ".mj2",
        ".svg", ".svgz", ".ai", ".eps", ".pdf"};
    private String[] dangerousEx = {".zip", ".rar", ".exe", ".7z", ".msi",
        ".jar", ".bat", ".cmd", ".js", ".vb", ".vbs", ".psc1"};
    private final String[] heaEx;
    private final String[] encodingType = {"7bit", "8bit", "binary", "Quoted-Printable", "base64"};
    List<String> encodeList = asList(encodingType);
    private final String[] contentTypes = {"text/plain", "text/html",
        "application/pdf", "image/gif", "multipart/alternative", "multipart/mixed",
        "multipart/encrypted", "multipart/signed", "multipart/related",
        "multipart/report", "message/rfc822", "multipart/digest", "multipart/byterange",
        "multipart/form-data", "multipart/parallel", "multipart/x-mixed-replaced"};
    List<String> list = asList(contentTypes);
    private final String[] dispositionType = {"inline", "attachment", "form-data"};
    List<String> dispList = asList(dispositionType);
    private final String[] shortUrl = {"goo.gl", "bit.ly", "tinyurl.com",
        "qr.ae", "cur.lv", "ow.ly", "u.to", "cutt.us", "buxurl.com", "qr.net", "tr.im"};

    //constructor

    /**
     *
     * @param message
     * @throws MessagingException
     */
    public StaticPhPhMessage(MimeMessage message) throws MessagingException {
        super(message);
        this.heaEx = new String[]{"Delivered-To", "Received", "X-Received", "ARC-Seal", "ARC-Message-Signature", "ARC-Authentication-Results", "Return-Path", "Received", "Received-SPF", "Authentication-Results", "DKIM-Signature", "X-Google-DKIM-Signature", "X-GM-Message-State", "X-Google-Smtp-Source", "X-Received", "MIME-Version", "From", "Date", "Message-ID", "Subject", "To", "Content-Type"};
        this.uniqueBody = new HashSet();
        this.message = message;
        messageMap = new HashMap<>();
    }

    /**
     * Feature 1 The number of “Content-Type: multipart/mixed” fields found in
     * the email’s body.
     *
     * Looks for Content-Type: multipart/mixed within an email.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws java.io.IOException
     */
    public int countTypeMixed(MimePart part) throws MessagingException, IOException {
        methodName = "countTypeMixed";
        if (part.isMimeType("multipart/mixed")) {
            mixed++;
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countTypeMixed((MimePart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, mixed);
        return mixed;
    }

    /**
     * Feature 2 The number of “Content-Disposition: attachment” fields found in
     * the email’s body.
     *
     * Parses mimemessage, searching for attachments. If mimemessage is
     * multipart it uses recursion to ensure the entire message is read.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countDispAttchmnts(MimePart part) throws MessagingException, IOException {
        methodName = "countDispAttchmnts";
        if (part.isMimeType("text/*") && part.getDisposition() != null
                || ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || !INLINE.equalsIgnoreCase(part.getDisposition())) {
            attach++;
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countDispAttchmnts((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, attach);
        return attach;
    }

    /**
     * Feature 3 The time span range between the email’s sent and received time.
     * Measured in minutes
     *
     * This methods is currently testing wrong, there are several issues with
     * POP3 and STMP retrieving a received date, the method attempts to extract
     * the date from the first found received header using a pattern with only
     * one successful pattern.
     *
     * @return
     * @throws MessagingException
     * @throws java.text.ParseException
     */
    public int totalEmailSpanTime() throws MessagingException, java.text.ParseException {
        long timespan = 0;
        methodName = "totalEmailSpanTime";
        Date recDate = null;
        //assign date values
        sentDate = this.getSentDate();
        String receivedHeaderFormat = "EEE, d MMM yyyy HH:mm:ss Z";
        String recRegex = "^[^;]+;(.+)$";
        if (this.getReceivedDate() != null) {
            receivedDate = this.getReceivedDate();
        }
        //Attempting to extract the date/time from the received header. 
        String[] receivedHeader = this.getHeader("Received");
        SimpleDateFormat sdf = new SimpleDateFormat(receivedHeaderFormat);
        for (String rh : receivedHeader) {
            Pattern pat = compile(recRegex);
            Matcher match = pat.matcher(rh);
            if (match.matches()) {
                String regMatch = match.group(1);
                if (regMatch != null) {
                    regMatch = regMatch.trim();
                    recDate = sdf.parse(regMatch);
                }
            }
        }
        //if received date isn't found, use the pattern matched recDate.
        if (receivedDate == null && recDate != null) {
            timespan = (int) MILLISECONDS.toSeconds(recDate.getTime() - sentDate.getTime());
        } else if (receivedDate != null) {
            timespan = (int) MILLISECONDS.toSeconds(receivedDate.getTime() - sentDate.getTime());
        }

        //add feature record to HashMap
        messageMap.put(methodName, (int) timespan);
        return (int) timespan;
    }

    /**
     * Feature 4 The number of unique “Content-Transfer-Encoding” fields found
     * in the email’s body.
     *
     * Looks for the Content-Transfer-Encoding fields, stores found fields in a
     * hashset to retrieve unique encoding. --Could easily change this to use
     * the arraylist from another method, convert to hashset and count.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countUniqueTransferEncode(MimePart part) throws MessagingException, IOException {
        methodName = "countUniqueTransferEncode";
        if (part.isMimeType("text/*") && part.getDisposition() == null
                || !ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || !INLINE.equalsIgnoreCase(part.getDisposition())) {
            String encoding = part.getEncoding();
            uniqueEncoding.add(encoding);
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countUniqueTransferEncode((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, uniqueEncoding.size());
        return uniqueEncoding.size();
    }

    /**
     * Feature 5 The number of unique “Content-Disposition” fields found in the
     * email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws java.io.IOException
     */
    public int countUniqueDisposition(MimePart part) throws MessagingException, IOException {
        methodName = "countUniqueDisposition";
        if (part.isMimeType("text/*") && INLINE.equalsIgnoreCase(part.getDisposition())
                || (ATTACHMENT.equalsIgnoreCase(part.getDisposition()))) {
            uniqueDisp.add(part.getDisposition());
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countUniqueDisposition((MimePart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, uniqueDisp.size());
        return uniqueDisp.size();
    }

    /**
     * Feature 6 The total number of “Content-Disposition” fields found in the
     * email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws java.io.IOException
     */
    public int countTotalDisposition(MimePart part) throws MessagingException, IOException {
        methodName = "countTotalDisposition";

        if (part.isMimeType("text/*") && part.getDisposition() != null
                || ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || INLINE.equalsIgnoreCase(part.getDisposition())) {
            value++;
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countTotalDisposition((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 7 Represents the total size of all attachments found in the
     * email.
     *
     * @param part
     * @return @throws MessagingException
     * @throws IOException
     */
    public int sumTotalSizeOfAttachmnts(MimePart part) throws MessagingException, IOException {
        int size = 0;
        methodName = "sumTotalSizeOfAttachmnts";
        if (part.isMimeType("multipart/*")) {
            Multipart mmp = (Multipart) part.getContent();
            for (int i = 0; i < mmp.getCount(); i++) {
                BodyPart bp = mmp.getBodyPart(i);
                if (ATTACHMENT.equalsIgnoreCase(bp.getDisposition())) {
                    size = bp.getSize();
                } else {
                    sumTotalSizeOfAttachmnts((MimeBodyPart) bp);
                }

            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, size);
        return size;
    }

    /**
     * Feature 8 The number of unique “Content-Type” fields found in the email’s
     * body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countUniqueType(MimePart part) throws MessagingException, IOException {
        methodName = "countUniqueType";
        if (part.isMimeType("text/*") && part.getDisposition() == null
                || ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || INLINE.equalsIgnoreCase(part.getDisposition())) {
            uniqueType.add(part.getContentType());
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                uniqueType.add(mp.getContentType());
                for (int i = 0; i < mp.getCount(); i++) {
                    countUniqueType((MimePart) mp.getBodyPart(i));

                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, uniqueType.size());
        return uniqueType.size();
    }

    /**
     * Feature 9 The total number of “Content-Transfer-Encoding” fields found in
     * the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countTotalTransferEncode(MimePart part) throws MessagingException, IOException {
        methodName = "countTotalTransferEncode";
        String encoding;
        if (part.isMimeType("text/*") && part.getDisposition() == null
                || ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || INLINE.equalsIgnoreCase(part.getDisposition())) {
            encoding = part.getEncoding();
            if (encoding != null) {
                cte.add(encoding);
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    MimePart current = (MimeBodyPart) mp.getBodyPart(i);
                    countTotalTransferEncode(current);
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, cte.size());
        return cte.size();
    }

    /**
     * Feature 10 The total number of “Content-Type” fields found in the email’s
     * body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countTotalType(MimePart part) throws MessagingException, IOException {
        methodName = "countTotalType";
        if (part.isMimeType("text/*") && part.getDisposition() == null
                || ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || INLINE.equalsIgnoreCase(part.getDisposition())) {
            contentTypeList.add(part.getContentType());
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                contentTypeList.add(mp.getContentType());
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart bp = mp.getBodyPart(i);
                    countTotalType((MimeBodyPart) bp);
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, contentTypeList.size());
        return contentTypeList.size();
    }

    /**
     * Feature 11 The number unrecognized of “Content-Type” fields found in the
     * email’s body.
     *
     * Using a delimiter to only retrieve the content type fields, ignore the
     * parameters. This avoids any accidental counting of the boundary/charset
     * params.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countTotalTypeUnkwn(MimePart part) throws MessagingException, IOException {
        String type;
        methodName = "countTotalTypeUnkwn";
        if (part.isMimeType("text/*") && part.getDisposition() == null
                || ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || INLINE.equalsIgnoreCase(part.getDisposition())) {
            String[] contentDelim = part.getContentType().split(";");
            type = contentDelim[0];
            if (!list.contains(type)) {
                notRecognized++;
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                String[] contentDelim = part.getContentType().split(";");
                type = contentDelim[0];
                if (!list.contains(type)) {
                    notRecognized++;
                }
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart bp = mp.getBodyPart(i);
                    countTotalTypeUnkwn((MimeBodyPart) bp);
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, notRecognized);
        return notRecognized;
    }

    /**
     * Feature 12 The unique number of MIME parts found in the email’s body when
     * the email is parsed using the JavaMail library.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countTotalMIMEParts(MimePart part) throws MessagingException, IOException {
        methodName = "countTotalMIMEParts";
        if (part.isMimeType("text/*") && part.getDisposition() == null
                || ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || INLINE.equalsIgnoreCase(part.getDisposition())) {
            mimePartsTotal++;
            uniqueBody.add(part.getContentType());
        } else {
            if (part.isMimeType("multipart/*")) {
                mimePartsTotal++;
                uniqueBody.add(part.getContentType());
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart bp = mp.getBodyPart(i);
                    countTotalMIMEParts((MimeBodyPart) bp);
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, mimePartsTotal);
        return mimePartsTotal;
    }

    /*Feature 13
    * The number of characters in the email’s subject.
    * Simply retrieves the subject line of the email and counts the 
    * length.
    * @return
    * @throws MessagingException 
     */

    /**
     *
     * @return
     * @throws MessagingException
     */

    public int lengthMsgSubject() throws MessagingException {
        int count = 0;
        methodName = "lengthMsgSubject";
        subject = this.getSubject();
        for (int i = 0; i < subject.length(); i++) {
            count++;
        }
        //add feature record to HashMap
        messageMap.put(methodName, count);
        return count;
    }

    /**
     * Feature 14 The number of characters in the email’s plaintext content.
     *
     * The feature is testing wrong. Attempted to use ByteArrayOutputStream to
     * to help count encoded message. The result is closer to previous attempts.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countPlainTxtChar(MimePart part) throws MessagingException, IOException {

        methodName = "countPlainTxtChar";
        if (part.isMimeType("text/*") && part.getDisposition() == null
                || ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || INLINE.equalsIgnoreCase(part.getDisposition())) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            part.getDataHandler().writeTo(bos);
            String decode = bos.toString();
            if (decode != null) {
                for (int i = 0; i < decode.length(); i++) {
                    length++;
                }
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart current = (BodyPart) mp.getBodyPart(i);
                    countPlainTxtChar((MimeBodyPart) current);
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, length);
        return length;
    }

    /**
     * Feature 15 The number of InputStream type MIME parts found in the email’s
     * body. ------------INCOMPLETE-----------------
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countMimeInputStrm(MimePart part) throws MessagingException, IOException {
        InputStream is;
        methodName = "countMimeInputStrm";
        if (part.isMimeType("text/*") && part.getDisposition() == null
                || ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || INLINE.equalsIgnoreCase(part.getDisposition())) {
            is = part.getDataHandler().getInputStream();
            value = 0;
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart bp = mp.getBodyPart(i);
                    //is = bp.getDataHandler().getInputStream();
                    countMimeInputStrm((MimeBodyPart) bp);
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 16 The number of Multipart type MIME parts found in the email’s
     * body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countMimeMultipart(MimePart part) throws MessagingException, IOException {
        methodName = "countMimeMultipart";
        if (part.isMimeType("multipart/*")) {
            multi++;
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                countMimeMultipart((MimeBodyPart) mp.getBodyPart(i));
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, multi);
        return multi;
    }

    /**
     * Feature 17 The number of attachments with content that does not match the
     * file extension. For example, content of an ∗.exe file with an ∗.txt file
     * extension.
     *
     * @return @throws MessagingException
     * @throws IOException
     */
    public int countMismatchMimeTypeAttachmtType() throws MessagingException, IOException {
        value = 0;
        int count = 0;
        methodName = "countMismatchMimeTypeAttachmtType";
        contentType = message.getContentType();
        contentType = this.getContentType();
        if (contentType.contains("multipart")) {
            //check if message includes attachments
            multipart = (MimeMultipart) this.getContent();
            //parse through each message bodypart
            for (int i = 0; i < multipart.getCount(); i++) {
                MimeBodyPart bodyPart = (MimeBodyPart) multipart.getBodyPart(i);
                //check if bodypart is an attachment
                if (ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())) {
                    File f = new File("/tmp/" + bodyPart.getFileName());
                    String contentType1 = probeContentType(f.toPath());
                    FileTypeMap ftm = null;
                    if (ftm != null) {
                        String contentType2 = ftm.getContentType(f);
                        if (!contentType1.equals(contentType2)) {
                            count++;
                        }
                    }
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, count);
        return count;
    }

    /**
     * Feature 18 The number of “Content-Transfer-Encoding: 7bit” fields found
     * in the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countTransferEncode7bit(MimePart part) throws MessagingException, IOException {
        methodName = "countTransferEncode7bit";
        if (part.isMimeType("text/*") && part.getDisposition() == null
                || ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || INLINE.equalsIgnoreCase(part.getDisposition())) {
            String encoding = part.getEncoding();
            if (encoding != null && encoding.equalsIgnoreCase("7bit")) {
                value++;
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    MimePart current = (MimePart) mp.getBodyPart(i);
                    countTransferEncode7bit(current);
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 19 The total number of MIME parts found in the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countTotalMime(MimePart part) throws MessagingException, IOException {
        methodName = "countTotalMime";
        if (part.isMimeType("text/*") && part.getDisposition() == null
                || ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || INLINE.equalsIgnoreCase(part.getDisposition())) {
            value++;
        } else {
            if (part.isMimeType("multipart/*")) {
                value++;
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countTotalMime((MimePart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 20 The number of “boundary = ” fields found in the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countTotalBoundaryEqs(MimePart part) throws MessagingException, IOException {
        value = 0;
        methodName = "countTotalBoundaryEqs";
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            ContentType ct = new ContentType(mp.getContentType());
            String bound = ct.getParameter("boundary");
            boundaryFields.add(bound);
            for (int i = 0; i < mp.getCount(); i++) {
                countTotalBoundaryEqs((MimeBodyPart) mp.getBodyPart(i));
            }
        }

        //add feature record to HashMap
        messageMap.put(methodName, boundaryFields.size());
        return boundaryFields.size();
    }

    /**
     * Feature 21 The number of “Content-Type: text/plain” fields found in the
     * email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countTypeTxtPln(MimePart part) throws MessagingException, IOException {
        value = 0;
        methodName = "countTypeTxtPln";
        if (part.isMimeType("text/plain") && part.getDisposition() == null
                || !ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || !INLINE.equalsIgnoreCase(part.getDisposition())) {
            value++;
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart bp = mp.getBodyPart(i);
                    //else recursively parse through the body parts    
                    countTypeTxtPln((MimeBodyPart) bp);
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 22 The version in the “MIME-Version” header field, for example
     * “MIME-Version: 1.0. ”
     *
     * @return @throws MessagingException
     */
    public int verifyMimeVersion() throws MessagingException {
        int version = 0;
        methodName = "verifyMimeVersion";
        Enumeration<Header> en = this.getAllHeaders();
        while (en.hasMoreElements()) {
            Header next = en.nextElement();
            if (next.getName().equalsIgnoreCase("mime-version")) {
                Float headerValue = parseFloat(next.getValue());
                version = round(headerValue);
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, version);
        return version;
    }

    /**
     * Feature 23 Indicates whether the “MIME-Version” field exists in the
     * email’s header.
     *
     * @return @throws MessagingException
     */
    public boolean checkExistsMimeVersion() throws MessagingException {
        int exist = 0;
        value = 0;
        methodName = "checkExistsMimeVersion";
        String[] version = this.getHeader("MIME-Version");
        if (version != null) {
            versionExists = true;
            exist = 1;
        }
        //add feature record to HashMap
        messageMap.put(methodName, exist);
        return versionExists;
    }

    /**
     * Feature 24 The number of URLs that contain a query string found in the
     * email’s body.
     *
     * @param part
     * @return @throws MessagingException
     * @throws java.io.IOException
     * @throws java.net.URISyntaxException
     */
    public int countURLQueryStrgs(MimePart part) throws MessagingException, IOException, URISyntaxException {
        String foundLink;
        foundLink = null;
        methodName = "countURLQueryStrgs";
        if (part.isMimeType("text/*") && part.getDisposition() == null) {
            String input = (String) part.getContent();
            Pattern linkPat = compile("href=\"(.*?)\"",
                    CASE_INSENSITIVE | DOTALL);
            Matcher match = linkPat.matcher(input);
            while (match.find()) {
                foundLink = match.group(1);
            }
            if (foundLink != null) {
                foundLink = foundLink.trim();
                URI uri = new URI(foundLink);
                String query = uri.getQuery();
                if (query != null) {
                    value++;
                }
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart bp = mp.getBodyPart(i);
                    countURLQueryStrgs((MimeBodyPart) bp);
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 25 The number of attachments that are considered dangerous.
     *
     * @return @throws MessagingException
     * @throws IOException
     */
    public int countDangerousAttchmnts() throws MessagingException, IOException {
        int count = 0;
        methodName = "countDangerousAttchmnts";
        contentType = message.getContentType();
        if (contentType.contains("multipart")) {
            //check if message includes attachments
            multipart = (MimeMultipart) message.getContent();
            //parse through each message bodypart
            for (int i = 0; i < multipart.getCount(); i++) {
                MimeBodyPart bodyPart = (MimeBodyPart) multipart.getBodyPart(i);
                //check if bodypart is an attachment
                if (ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())) {
                    //iterate through each file extension
                    for (String dangerousEx1 : dangerousEx) {
                        if (bodyPart.getFileName().contains(dangerousEx1)) {
                            count++;
                        }
                    }
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, count);
        return count;
    }

    /**
     * Feature 26 The number of characters in the “MIME-Version” header field.
     *
     * @return @throws MessagingException
     */
    public int lengthMimeVersionHdr() throws MessagingException {
        value = 0;
        int header = 0;
        methodName = "lengthMimeVersionHdr";
        Enumeration<Header> en = this.getAllHeaders();
        while (en.hasMoreElements()) {
            Header next = en.nextElement();
            if (next.getName().equalsIgnoreCase("mime-version")) {
                String headerValue = next.getValue();
                out.println(headerValue);
                for (int i = 0; i < headerValue.length(); i++) {
                    header++;
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, header);
        return header;
    }

    /**
     * Feature 27 The number of text sentences found in the email’s body (both
     * plaintext and HTML content).
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws java.io.IOException
     */
    public int countSentences(MimePart part) throws MessagingException, IOException {
        value = 0;
        if (part.isMimeType("text/*")) {
            body = (String) part.getContent();
            subString = ". ";
            //calculate frequency of ". " (period with space) that indicates a sentence
            sentence = (body.length() - body.replace(subString, "").length()) / subString.length();
            //check if feature was already added before recursion
            if (messageMap.containsKey(methodName)) {
                messageMap.replace(methodName, messageMap.get(methodName) + value);
            } else {
                //add feature record to HashMap
                messageMap.put(methodName, value);
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    MimePart mpart = (MimePart) mp.getBodyPart(i);
                    countSentences(mpart);
                }
            }
        }
        return sentence;
    }

    /**
     * Feature 28 The number of “Content-Transfer-Encoding: 8bit” fields found
     * in the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countTransferEncode8bit(MimePart part) throws MessagingException, IOException {

        methodName = "countTransferEncode8bit";
        if (part.isMimeType("text/*") && part.getDisposition() == null
                || ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || INLINE.equalsIgnoreCase(part.getDisposition())) {
            String encoding = part.getEncoding();
            if (encoding != null && encoding.equalsIgnoreCase("8bit")) {
                value++;
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    MimePart current = (MimePart) mp.getBodyPart(i);
                    countTransferEncode8bit(current);
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 29 The number of “Content-Type: multipart/encrypted” fields found
     * in the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws java.io.IOException
     */
    public int countTypeEncrypted(MimePart part) throws MessagingException, IOException {
        methodName = "countTypeEncrypted";
        if (part.isMimeType("multipart/encrypted")) {
            value++;
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countTypeEncrypted((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 30 The number of “Content-Type: …; charset = " iso 2022-jp”
     * fields found in the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countCharsetIso2202_jp(MimePart part) throws MessagingException, IOException {
        methodName = "countCharsetIso2202_jp";
        String charset;
        if (part.isMimeType("text/*") && part.getDisposition() == null) {
            ContentType ct = new ContentType(part.getContentType());
            charset = ct.getParameter("charset");
            if (charset.equalsIgnoreCase("Iso2202_jp")) {
                value++;
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countCharsetIso2202_jp((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 31 The number of “Content-Type: …; charset = "us-ascii” fields
     * found in the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countCharsetUs_ascii(MimePart part) throws MessagingException, IOException {
        methodName = "countCharsetUs_ascii";
        String charset;
        if (part.isMimeType("text/*") && part.getDisposition() == null) {
            ContentType ct = new ContentType(part.getContentType());
            charset = ct.getParameter("charset");
            if (charset.equalsIgnoreCase("us-ascii")) {
                value++;
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countCharsetUs_ascii((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 32 The number of unique charsets found in the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countUniqueCharsets(MimePart part) throws MessagingException, IOException {
        value = 0;
        methodName = "countUniqueCharsets";
        String charset;
        if (part.isMimeType("text/*") && part.getDisposition() == null) {
            ContentType ct = new ContentType(part.getContentType());
            charset = ct.getParameter("charset");
            uniqueChar.add(charset);
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countUniqueCharsets((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, uniqueChar.size());
        return uniqueChar.size();
    }

    /**
     * Feature 33 The number of _ iframe _ tags in the HTML content.
     *
     * @param part
     * @return @throws MessagingException
     * @throws java.io.IOException
     */
    public int countIFrameTags(MimePart part) throws MessagingException, IOException {

        methodName = "countIFrameTags";
        if (part.isMimeType("text/html")) {
            body = (String) part.getContent();
            subString = "<iframe";
            //calculate frequency of "<iframe" within HTML content
            frames = (body.length() - body.replace(subString, "").length()) / subString.length();
            //check if feature was already added before recursion
            if (messageMap.containsKey(methodName)) {
                messageMap.replace(methodName, messageMap.get(methodName) + frames);
            } else {
                //add feature record to HashMap
                messageMap.put(methodName, frames);
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    MimePart mpart = (MimePart) mp.getBodyPart(i);
                    if (mpart.isMimeType("text/html")) {
                        countIFrameTags(mpart);
                    }
                }
            }
        }
        //add feature record to HashMap
        return frames;
    }

    /**
     * Feature 34 The number of “Content-Transfer-Encoding: binary” fields found
     * in the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countTransferEncodeBin(MimePart part) throws MessagingException, IOException {
        methodName = "countTransferEncodeBin";
        if (part.isMimeType("text/*") && part.getDisposition() == null
                || ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || INLINE.equalsIgnoreCase(part.getDisposition())) {
            String encoding = part.getEncoding();
            if (encoding != null && encoding.equalsIgnoreCase("binary")) {
                value++;
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    MimePart current = (MimePart) mp.getBodyPart(i);
                    countTransferEncodeBin(current);
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 35 The number of “Content-Type: multipart/signed” fields found in
     * the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws java.io.IOException
     */
    public int countTypeSigned(MimePart part) throws MessagingException, IOException {
        methodName = "countTypeSigned";
        if (part.isMimeType("multipart/signed")) {
            value++;
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countTypeSigned((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 36 The number of unique domains found in URLs in the email’s
     * body.
     *
     * @param part
     * @return @throws MessagingException
     * @throws java.net.URISyntaxException
     * @throws java.io.IOException
     */
    public int countUniqueDomainsInURLs(MimePart part) throws MessagingException, URISyntaxException, IOException {
        value = 0;
        methodName = "countUniqueDomainsInURLs";
        String foundLink = null;
        if (part.isMimeType("text/*") && part.getDisposition() == null) {
            String input = (String) part.getContent();
            Pattern linkPat = compile("href=\"(.*?)\"",
                    CASE_INSENSITIVE | DOTALL);
            Matcher match = linkPat.matcher(input);
            while (match.find()) {
                foundLink = match.group(1);
            }
            if (foundLink != null) {
                foundLink = foundLink.trim();
                URI uri = new URI(foundLink);
                String domain = uri.getHost();
                uniqueDomains.add(domain);
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart bp = mp.getBodyPart(i);
                    countUniqueDomainsInURLs((MimeBodyPart) bp);
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, uniqueDomains.size());
        return uniqueDomains.size();
    }

    /**
     * Feature 37 Counts the total urls found in the email body. Source for this
     * extraction:
     * https://stackoverflow.com/questions/5713558/detect-and-extract-url-from-a-string
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countTotalURLs(MimePart part) throws MessagingException, IOException {
        methodName = "countTotalURLs";
        if (part.isMimeType("text/*") && part.getDisposition() == null) {
            String input = (String) part.getContent();
            Pattern linkPat = compile("href=\"(.*?)\"",
                    CASE_INSENSITIVE | DOTALL);
            Matcher match = linkPat.matcher(input);
            while (match.find()) {
                String foundLink = match.group(1);
                links.add(foundLink);
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart bp = mp.getBodyPart(i);
                    countTotalURLs((MimeBodyPart) bp);
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, links.size());
        return links.size();
    }

    /**
     * Feature 38 The number of unique domains found in the email.
     *
     * @return
     * @throws MessagingException
     * @throws URISyntaxException
     */
    public int countUniqueDomains() throws MessagingException, URISyntaxException {
        value = 0;
        methodName = "countUniqueDomains";
        for (String l : links) {
            l = l.trim();
            URI uri = new URI(l);
            String domain = uri.getHost();
            uniqueDomains.add(domain);

        }
        //add feature record to HashMap
        messageMap.put(methodName, uniqueDomains.size());
        return uniqueDomains.size();
    }

    /**
     * Feature 39 The total number of charsets found in the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countTotalCharsets(MimePart part) throws MessagingException, IOException {
        methodName = "countTotalCharsets";
        String charset;
        if (part.isMimeType("text/*") && part.getDisposition() == null
                || ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || INLINE.equalsIgnoreCase(part.getDisposition())) {
            ContentType ct = new ContentType(part.getContentType());
            charset = ct.getParameter("charset");
            if (charset != null) {
                charsetValues.add(charset);
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countTotalCharsets((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, charsetValues.size());
        return charsetValues.size();
    }

    /**
     * Feature 40 The number of attachments that belong to the video category
     * (e.g., ∗.mp4, ∗.avi, etc.).
     *
     * @return @throws MessagingException
     * @throws IOException
     */
    public int countVideoAttachmnts() throws MessagingException, IOException {
        int video = 0;
        methodName = "countVideoAttachmnts";
        contentType = message.getContentType();
        if (contentType.contains("multipart")) {
            //check if message includes attachments
            multipart = (MimeMultipart) message.getContent();
            //parse through each message bodypart
            for (int i = 0; i < multipart.getCount(); i++) {
                MimeBodyPart bodyPart = (MimeBodyPart) multipart.getBodyPart(i);
                //check if bodypart is an attachment
                if (ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())) {
                    //iterate through each file extension
                    for (String videoEx1 : videoEx) {
                        if (bodyPart.getFileName().contains(videoEx1)) {
                            video++;
                        }
                    }
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, video);
        return video;
    }

    /**
     * Feature 41 The number of attachments that belong to the image category
     * (e.g., ∗.jpg, ∗.png, etc.).
     *
     * @return @throws MessagingException
     * @throws IOException
     */
    public int countImageAttachmnts() throws MessagingException, IOException {
        int image = 0;
        methodName = "countImageAttachmnts";
        contentType = message.getContentType();
        if (contentType.contains("multipart")) {
            //check if message includes attachments
            multipart = (MimeMultipart) message.getContent();
            //parse through each message bodypart
            for (int i = 0; i < multipart.getCount(); i++) {
                MimeBodyPart bodyPart = (MimeBodyPart) multipart.getBodyPart(i);
                //check if bodypart is an attachment
                if (ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())) {
                    //iterate through each file extension
                    for (String videoEx1 : videoEx) {
                        if (bodyPart.getFileName().contains(videoEx1)) {
                            image++;
                        }
                    }
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, image);
        return image;
    }

    /**
     * Feature 42 The number of attachments that belong to the digital signature
     * category (e.g., ∗.p7s).
     *
     * @return @throws MessagingException
     * @throws IOException
     */
    public int countDigSignAttachmnts() throws MessagingException, IOException {
        int dig = 0;
        methodName = "countDigSignAttachmnts";
        contentType = message.getContentType();
        if (contentType.contains("multipart")) {
            //check if message includes attachments
            multipart = (MimeMultipart) message.getContent();
            //parse through each message bodypart
            for (int i = 0; i < multipart.getCount(); i++) {
                MimeBodyPart bodyPart = (MimeBodyPart) multipart.getBodyPart(i);
                //check if bodypart is an attachment
                if (ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())) {
                    if (bodyPart.getFileName().contains(".p7s")) {
                        dig++;
                    }
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, dig);
        return dig;
    }

    /**
     * Feature 43 The number of MIME parts found in the email’s body that cause
     * an error when parsing with the JavaMail package. (This should most likely
     * be a custom exception!)
     *
     * @return @throws MessagingException
     */
    public int countErrorsParsing() throws MessagingException {
        value = 0;
        methodName = "countErrorsParsing";
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 44 The number of unknown or non-standard
     * "Content-Transfer-Encoding” values.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countTransferEncodeUnkwn(MimePart part) throws MessagingException, IOException {
        String type;
        methodName = "countTransferEncodeUnkwn";
        if (part.isMimeType("text/*") && part.getDisposition() == null
                || ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || INLINE.equalsIgnoreCase(part.getDisposition())) {
            if (part.getEncoding() != null) {
                type = part.getEncoding();
                if (!encodeList.contains(type)) {
                    notRecognized++;
                }
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart bp = mp.getBodyPart(i);
                    countTransferEncodeUnkwn((MimeBodyPart) bp);
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, notRecognized);
        return notRecognized;
    }

    /**
     * Feature 45 The number of attachments that are part of the text category
     * (e.g., ∗.txt, ∗.xml, ∗.csv).
     *
     * @return @throws MessagingException
     * @throws IOException
     */
    public int countTxtAttachmnts() throws MessagingException, IOException {
        int text = 0;
        methodName = "countTxtAttachmnts";
        contentType = message.getContentType();
        if (contentType.contains("multipart")) {
            //check if message includes attachments
            multipart = (MimeMultipart) message.getContent();
            //parse through each message bodypart
            for (int i = 0; i < multipart.getCount(); i++) {
                MimeBodyPart bodyPart = (MimeBodyPart) multipart.getBodyPart(i);
                //check if bodypart is an attachment
                if (ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())) {
                    //iterate through each file extension
                    for (String textEx1 : textEx) {
                        if (bodyPart.getFileName().contains(textEx1)) {
                            text++;
                        }
                    }
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, text);
        return text;
    }

    /**
     * Feature 46 The number of “Content-Type: multipart/related” fields found
     * in the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countTypeRelated(MimePart part) throws MessagingException, IOException {
        methodName = "countTypeRelated";
        if (part.isMimeType("multipart/related")) {
            value++;
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countTypeRelated((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 47 The number of _ link _ tags in the HTML content.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countLinksInHTML(MimePart part) throws MessagingException, IOException {
        methodName = "countLinksInHTML";
        Pattern linkPattern = compile("<a[^>]+href=[\"']?([\"'>]+)[\\\"']?[^>]*>(.+?)<\\/a>",
                CASE_INSENSITIVE | DOTALL);
        ArrayList<String> foundLinks = new ArrayList<>();
        if (part.isMimeType("text/html")) {
            String htmlLinks = (String) part.getContent();
            Matcher match = linkPattern.matcher(htmlLinks);
            while (match.find()) {
                foundLinks.add(match.group());
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart bp = mp.getBodyPart(i);
                    countLinksInHTML((MimeBodyPart) bp);
                }
            }
        }

        //add feature record to HashMap
        messageMap.put(methodName, foundLinks.size());
        return foundLinks.size();
    }

    /**
     * Feature 48 The number of unrecognized attachment types.
     *
     * @return @throws MessagingException
     * @throws IOException
     */
    public int countUnkwnAttachmnts() throws MessagingException, IOException {
        isRecognized = false;
        methodName = "countUnkwnAttachmnts";
        contentType = message.getContentType();
        if (contentType.contains("multipart")) {
            //check if message includes attachments
            multipart = (MimeMultipart) message.getContent();
            //parse through each message bodypart
            for (int i = 0; i < multipart.getCount(); i++) {
                MimeBodyPart bodyPart = (MimeBodyPart) multipart.getBodyPart(i);
                //check if bodypart is an attachment
                if (ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())) {
                    while (isRecognized = false) {
                        //iterate through each list of file extensions
                        for (String textEx1 : textEx) {
                            if (bodyPart.getFileName().contains(textEx1)) {
                                isRecognized = true;
                            }
                        }
                        for (String videoEx1 : videoEx) {
                            if (bodyPart.getFileName().contains(videoEx1)) {
                                isRecognized = true;
                            }
                        }
                        for (String imageEx1 : imageEx) {
                            if (bodyPart.getFileName().contains(imageEx1)) {
                                isRecognized = true;
                            }
                        }
                        if (bodyPart.getFileName().contains(".p7s")) {
                            isRecognized = true;
                        }
                        for (String audioEx1 : audioEx) {
                            if (bodyPart.getFileName().contains(audioEx1)) {
                                isRecognized = true;
                            }
                        }
                        if (isRecognized = false) {
                            notRecognized++;
                            isRecognized = false;
                        }
                    }
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, notRecognized);
        return notRecognized;
    }

    /**
     * Feature 49 The number of email addresses found in the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countEmlAddresses(MimePart part) throws MessagingException, IOException {
        methodName = "countEmlAddresses";
        Pattern p = compile("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}\\b", CASE_INSENSITIVE);
        Matcher match = p.matcher(INLINE);
        Set<String> foundEmails = new HashSet<>();
        while (match.find()) {
            foundEmails.add(match.group());
        }

        //add feature record to HashMap
        messageMap.put(methodName, foundEmails.size());
        return foundEmails.size();
    }

    /**
     * Feature 50 The length of the header part of the email in byes.
     *
     * Testing wrong.
     *
     * @return @throws MessagingException
     */
    public int lengthEmlHdrBytes() throws MessagingException {
        value = 0;
        int headerBytes = 0;
        methodName = "lengthEmlHdrBytes";
        byte[] headerVBytes;
        byte[] headerNBytes;
        Enumeration<Header> en = this.getAllHeaders();
        while (en.hasMoreElements()) {
            Header next = en.nextElement();
            String headerName = next.getName();
            headerNBytes = headerName.getBytes();
            String headerValue = next.getValue();
            headerVBytes = headerValue.getBytes();
            headerBytes += headerVBytes.length + headerNBytes.length;
        }

        //add feature record to HashMap
        messageMap.put(methodName, headerBytes);
        return headerBytes;
    }

    /**
     * Feature 51 The number of “Content-Type: …; charset = "utf-8 __ fields
     * found in the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countCharsetUtf8(MimePart part) throws MessagingException, IOException {
        methodName = "countCharsetUtf8";
        String charset;
        if (part.isMimeType("text/*") && part.getDisposition() == null) {
            ContentType ct = new ContentType(part.getContentType());
            charset = ct.getParameter("charset");
            if (charset.equalsIgnoreCase("UTF-8")) {
                value++;
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countCharsetUtf8((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 52 The number of URLs in the email body that are related to URL
     * shortening services (e.g., bitly, goo.gl, etc.).
     *
     * @param part
     * @return @throws MessagingException
     * @throws java.io.IOException
     */
    public int countShortenedURLs(MimePart part) throws MessagingException, IOException {
        int shortened = 0;
        value = 0;
        methodName = "countShortenedURLs";
        List<String> listOfUrls = new ArrayList<>();
        String urlRegex = "((https?|ftp|gopher|telnet|file):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)";
        Pattern pattern = compile(urlRegex, CASE_INSENSITIVE);
        if (part.isMimeType("text/*")) {
            body = (String) part.getContent();
            //find all URLs and place them in an ArrayList
            Matcher urlMatcher = pattern.matcher(body);
            while (urlMatcher.find()) {
                listOfUrls.add(body.substring(urlMatcher.start(0), urlMatcher.end(0)));
            }
            //check if URL is shortened
            for (String urlList : listOfUrls) {
                for (String shortUrl1 : shortUrl) {
                    if (urlList.contains(shortUrl1)) {
                        shortened++;
                    }
                }
            }
            //check if feature was already added before recursion
            if (messageMap.containsKey(methodName)) {
                messageMap.replace(methodName, messageMap.get(methodName) + shortened);
            } else {
                //add feature record to HashMap
                messageMap.put(methodName, shortened);
            }
        }
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                MimePart mpart = (MimePart) mp.getBodyPart(i);
                if (mpart.isMimeType("text/*")) {
                    countLangsInBody(mpart);
                }
            }
        }
        return shortened;
    }

    /**
     * Feature 53 The number of “Content-Type: multipart/report” fields found in
     * the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws java.io.IOException
     */
    public int countTypeReport(MimePart part) throws MessagingException, IOException {
        methodName = "countTypeReport";
        if (part.isMimeType("multipart/report")) {
            value++;
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countTypeReport((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 54 The number of “References” headers.
     *
     * @return @throws MessagingException
     */
    public int countReferencesHdrs() throws MessagingException {
        int count = 0;
        methodName = "countReferencesHdrs";
        String[] referenceHeader = this.getHeader("References");
        if (referenceHeader != null) {
            for (String rh : referenceHeader) {
                count++;
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, count);
        return count;
    }

    /**
     * Feature 55 The number of headers found in the email’s header.
     *
     * @return @throws MessagingException
     */
    public int countTotalHdrs() throws MessagingException {
        value = 0;
        int count = 0;
        methodName = "countTotalHdrs";
        Enumeration<Header> en = this.getAllHeaders();
        while (en.hasMoreElements()) {
            Header head = en.nextElement();
            if (head != null) {
                count++;
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, count);
        return count;
    }

    /**
     * Feature 56 The number of “Content-Transfer-Encoding: quoted-printable”
     * fields found in the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countTransferEncodeQuotedPrint(MimePart part) throws MessagingException, IOException {
        methodName = "countTransferEncodeQuotedPrint";
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                MimePart current = (MimePart) mp.getBodyPart(i);
                countTransferEncodeQuotedPrint(current);
            }
        } else {
            if (part.isMimeType("text/*") && part.getDisposition() == null
                    || ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                    || INLINE.equalsIgnoreCase(part.getDisposition())) {
                String encoding = part.getEncoding();
                if (encoding != null && encoding.equalsIgnoreCase("quoted-printable")) {
                    value++;
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * //Feature 57 //The number of unrecognized charsets found in the email’s
     * body.
     *
     * @param part
     * @return @throws MessagingException
     * @throws java.io.IOException
     */
    public int countCharsetsUnkwn(MimePart part) throws MessagingException, IOException {
        methodName = "countCharsetsUnkwn";
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart p = mp.getBodyPart(i);
                String set = (String) p.getContentType();
                if (set == null) {
                    notRecognized++;
                }
            }

        }
        //add feature record to HashMap
        messageMap.put(methodName, notRecognized);
        return notRecognized;
    }

    //Feature 58
    //The number of unique domain names found in email addresses in the email. 
    /**
     *
     * @return @throws MessagingException
     */
    public int countUniqueDomainsInEmlAddr() throws MessagingException {
        methodName = "countUniqueDomainsInEmlAddr";
        //find email addresses in "From"  header
        String senders = Arrays.toString(this.getFrom());
        ArrayList<String> senderList = new ArrayList<>();
        senderList.add(senders);
        //store unique domain to hashset
        String domain1 = senderList.get(0).substring(senderList.get(0).indexOf("@") + 1);
        uniqueEmlAddDomains.add(domain1);
        //find email addresses in email body
        methodName = "countEmlAddresses";
        Pattern p = compile("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}\\b", CASE_INSENSITIVE);
        Matcher match = p.matcher(INLINE);
        ArrayList<String> foundEmails = new ArrayList<>();
        while (match.find()) {
            foundEmails.add(match.group());
        }
        //store unique domain to hashset
        for (int i = 0; i < foundEmails.size(); i++) {
            String domain2 = foundEmails.get(i).substring(foundEmails.get(i).indexOf("@") + 1);
            uniqueEmlAddDomains.add(domain2);
        }
        //add feature record to HashMap
        messageMap.put(methodName, uniqueEmlAddDomains.size());
        return uniqueEmlAddDomains.size();
    }

    //Feature 59
    //The number of different languages found in the email’s body text. 
    //The languages are determined using the Tika Java package.
    /**
     *
     * @param part
     * @return @throws MessagingException
     * @throws java.io.IOException
     */
    public int countLangsInBody(MimePart part) throws MessagingException, IOException {
        methodName = "countLangsInBody";
        List<LanguageResult> lr = new ArrayList<>();
        LanguageDetector ld = null;
        if (part.isMimeType("text/*")) {
            body = (String) part.getContent();
            //detect all languages and place them in an ArrayList
            if (ld != null) {
                lr = ld.detectAll(body);
            }
            //count how many languages are in the ArrayList

            //check if feature was already added before recursion
            if (messageMap.containsKey(methodName)) {
                messageMap.replace(methodName, messageMap.get(methodName) + lr.size());
            } else {
                //add feature record to HashMap
                messageMap.put(methodName, lr.size());
            }
        }
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                MimePart mpart = (MimePart) mp.getBodyPart(i);
                if (mpart.isMimeType("text/*")) {
                    countLangsInBody(mpart);
                }
            }
        }
        //add feature record to HashMap
        // messageMap.put(methodName, value);
        return lr.size();
    }

    //Feature 60
    //The number of “Content-Type: text/html” fields found in the email’s body.
    /**
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countTypeTxtHtml(MimePart part) throws MessagingException, IOException {
        methodName = "countTypeTxtHtml";

        if (part.isMimeType("text/html")) {
            value++;
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    //else recursively parse through the body parts    
                    countTypeTxtHtml((MimePart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 61 The number of headers in which the name is not in the correct
     * case. For example: "MIME-Version" vs "Mime-Version".
     *
     * This method should be redone for more accuracy.
     *
     * @return @throws MessagingException
     */
    public int countHdrsWrongCase() throws MessagingException {
        value = 0;
        int count = 0;
        methodName = "countHdrsWrongCase";
        Enumeration<Header> headerValues = this.getAllHeaders();
        while (headerValues.hasMoreElements()) {
            Header header = headerValues.nextElement();
            ArrayList<String> heaNames = new ArrayList<>();
            heaNames.add(header.getName());
            for (String s : heaNames) {
                if (!heaNames.contains(s)) {
                    count++;
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, count);
        return count;
    }

    /**
     * Feature 62 The number of “Content-Type: multipart/alternative” fields
     * found in the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countTypeMultiAlt(MimePart part) throws MessagingException, IOException {
        methodName = "countTypeMultiAlt";
        if (part.isMimeType("multipart/alternative")) {
            value++;
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    //else recursively parse through the body parts    
                    countTypeMultiAlt((MimePart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 63 Indicates whether the “X-Spam-Status” field exists in the
     * email’s header.
     *
     * @return @throws MessagingException
     */
    public int checkExistsXSpamStat() throws MessagingException {
        int count = 0;
        methodName = "checkExistsXSpamStat";
        String[] referenceHeader = this.getHeader("X-Spam-Status");
        if (referenceHeader != null) {
            for (String rh : referenceHeader) {
                count++;
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, count);
        return count;
    }

    /**
     * Feature 64 The number of “Content-Type: ...; charset = "gb2312” fields
     * found in the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws java.io.IOException
     */
    public int countCharsetGb2312(MimePart part) throws MessagingException, IOException {
        methodName = "countCharsetGb2312";
        String charset;
        if (part.isMimeType("text/*") && part.getDisposition() == null) {
            ContentType ct = new ContentType(part.getContentType());
            charset = ct.getParameter("charset");
            if (charset.equalsIgnoreCase("gb2312")) {
                value++;
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    value = 0;
                    countCharsetGb2312((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 65 The number of “Content-Disposition: inline” fields found in
     * the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws java.io.IOException
     */
    public int countDispInline(MimePart part) throws MessagingException, IOException {
        value = 0;
        methodName = "countDispInline";
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                countDispInline((MimeBodyPart) mp.getBodyPart(i));
            }
        } else {
            if (part.isMimeType("text/*") && part.getDisposition() != null
                    || INLINE.equalsIgnoreCase(part.getDisposition())) {
                inline++;
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, inline);
        return inline;
    }

    /**
     * Feature 66 The number of “Content-Type: ...; charset = "shift-jis” fields
     * found in the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws java.io.IOException
     */
    public int countCharsetShift_jis(MimePart part) throws MessagingException, IOException {
        String charset;
        methodName = "countCharsetShift_jis";
        if (part.isMimeType("text/*") && part.getDisposition() == null) {
            ContentType ct = new ContentType(part.getContentType());
            charset = ct.getParameter("charset");
            if (charset.equalsIgnoreCase("shift-jis")) {
                value++;
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countCharsetShift_jis((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 67 The number of MimeMessage parts found in the email’s body.
     *
     * @param part
     * @return @throws MessagingException
     * @throws java.io.IOException
     */
    public int countMimeMessageParts(MimePart part) throws MessagingException, IOException {
        methodName = "countMimeMessageParts";
        if (part.isMimeType("text/*") && part.getDisposition() == null
                || ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                || INLINE.equalsIgnoreCase(part.getDisposition())) {
            value++;
        } else {
            if (part.isMimeType("multipart/*")) {
                value++;
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countMimeMessageParts((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }

        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 68 The number of “Content-Type: message/rfc822” fields found in
     * the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countTypeMsgRfc822(MimePart part) throws MessagingException, IOException {
        methodName = "countTypeMsgRfc822";
        if (part.isMimeType("message/rfc822")) {
            value++;
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    value = 0;
                    countTypeMsgRfc822((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 69 The number of “Content-Type: ...; charset = "utf-7 ## fields
     * found in the email’s body.
     *
     * The the parameter of content-type to get charset.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countCharsetUtf7(MimePart part) throws MessagingException, IOException {
        methodName = "countCharsetUtf7";
        String charset;
        if (part.isMimeType("text/*") && part.getDisposition() == null) {
            ContentType ct = new ContentType(part.getContentType());
            charset = ct.getParameter("charset");
            if (charset.equalsIgnoreCase("UTF-7")) {
                value++;
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    value = 0;
                    countCharsetUtf7((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 70 The number of forward strings (Forward/Fw:) found in the
     * email’s subject.
     *
     * @return @throws MessagingException
     */
    public int countForwards() throws MessagingException {
        int count = 0;
        methodName = "countForwards";
        subject = this.getSubject();
        subString = "Fw:";
        //calculate frequency of "Fw:" in subject line
        count = (subject.length() - subject.replace(subString, "").length()) / subString.length();
        //add feature record to HashMap
        messageMap.put(methodName, count);
        return count;
    }

    /**
     * Feature 71 The number of reply strings (Reply/ Re :) found in the email’s
     * subject.
     *
     * @return @throws MessagingException
     */
    public int countReplys() throws MessagingException {
        int count = 0;
        methodName = "countReplys";
        subject = this.getSubject();
        subString = "Re:";
        //calculate frequency of "Re:" in subject line
        count = (subject.length() - subject.replace(subString, "").length()) / subString.length();
        //add feature record to HashMap
        messageMap.put(methodName, count);
        return count;
    }

    /**
     * Feature 72 The number of senders in the “From” header.
     *
     * @return @throws MessagingException
     */
    public int countSenders() throws MessagingException {
        value = 0;
        methodName = "countSenders";
        String senders = Arrays.toString(this.getFrom());
        ArrayList<String> senderList = new ArrayList<>();
        senderList.add(senders);
        int count = senderList.size();
        //add feature record to HashMap
        messageMap.put(methodName, count);
        return count;
    }

    /**
     * //Feature 73 //The number of images in HTML that function as links.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countImagesAsLinks(MimePart part) throws MessagingException, IOException {
        value = 0;
        methodName = "countImagesAsLinks";
        Pattern p = compile("src=\"(.*?)\" /><br />(.*?)</div>");
        ArrayList<String> imageLinks = new ArrayList<>();
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) this.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                if (bp.isMimeType("text/html")) {
                    String image = (String) bp.getContent();
                    Matcher match = p.matcher(image);
                    if (match.find()) {
                        imageLinks.add(image);
                    }
                }
            }
        }
        int count = imageLinks.size();
        //add feature record to HashMap
        messageMap.put(methodName, count);
        return count;
    }

    /**
     * Feature 74 The number of attachments that belong to the audio category
     * (e.g., ∗.mp3, ∗ .wav, etc.).
     *
     * @return @throws MessagingException
     * @throws IOException
     */
    public int countAudioAttachmnts() throws MessagingException, IOException {
        int audio = 0;
        methodName = "countAudioAttachmnts";
        contentType = message.getContentType();
        if (contentType.contains("multipart")) {
            //check if message includes attachments
            multipart = (MimeMultipart) message.getContent();
            //parse through each message bodypart
            for (int i = 0; i < multipart.getCount(); i++) {
                MimeBodyPart bodyPart = (MimeBodyPart) multipart.getBodyPart(i);
                //check if bodypart is an attachment
                if (ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())) {
                    for (String audioEx1 : audioEx) {
                        if (bodyPart.getFileName().contains(audioEx1)) {
                            audio++;
                        }
                    }
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, audio);
        return audio;
    }

    /**
     * Feature 75 The number of MIME parts from String type, found in the
     * email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countMimePartStringType(MimePart part) throws MessagingException, IOException {
        methodName = "countMimePartStringType";
        if (part.isMimeType("text/*") && part.getDisposition() == null) {
            String encode = part.getEncoding();
            if (encode.equalsIgnoreCase("Quoted-Printable") || encode.equalsIgnoreCase("8bit")
                    || encode.equalsIgnoreCase("7bit")) {
                value++;
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countMimePartStringType((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 76 Indicates whether the email address in the "From” header field
     * complies with the standards set by the Internet RFCs.
     *
     * Uses regex to ensure the 'from' header values complies with RFC standards
     * http://emailregex.com/
     *
     * @return @throws MessagingException
     */
    public boolean checkFromComplies() throws MessagingException {
        value = 0;
        int count = 0;
        boolean isValid = false;
        Scanner scan;
        methodName = "checkFromComplies";
        Pattern pat = compile("(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])", CASE_INSENSITIVE);

        Address[] from = this.getFrom();
        InternetAddress fromAdd = (InternetAddress) from[0];
        String fromAddress = fromAdd.toString();
        scan = new Scanner(fromAddress);
        scan.useDelimiter("<");
        while (scan.hasNext()) {
            String trimmed = scan.next();
            Matcher match = pat.matcher(trimmed);
            while (match.find()) {
                isValid = true;
                count = 1;
            }
        }

        //add feature record to HashMap
        messageMap.put(methodName, count);
        return isValid;
    }

    /**
     * Feature 77 Indicates whether the email is a no-reply email.
     *
     * @return @throws MessagingException
     */
    public boolean checkExistsNoReply() throws MessagingException {
        value = 0;
        methodName = "checkExistsNoReply";
        int ex = 0;
        String replyTo = InternetAddress.toString(this.getReplyTo());
        if (replyTo != null) {
            if (replyTo.contains("no-reply")) {
                checkReply = true;
                ex = 1;
            }
        }

        //add feature record to HashMap
        messageMap.put(methodName, ex);
        return checkReply;
    }

    /**
     * Feature 78 Indicates whether the domains in the “From” and the “Reply-To”
     * headers are similar.
     *
     * Retrieves the Reply-To and From values, if Reply-To is empty, it is
     * assigned to the 'from' value
     *
     * @return @throws MessagingException
     */
    public boolean checkFromSameAsReplyTo() throws MessagingException {
        value = 0;
        String from, replyTo;
        boolean isSame = false;
        int count = 0;
        methodName = "checkFromSameAsReplyTo";
        replyTo = InternetAddress.toString(this.getReplyTo());
        from = InternetAddress.toString(this.getFrom());
        if (replyTo != null) {
            isSame = replyTo.equalsIgnoreCase(from);
            if (replyTo.equalsIgnoreCase(from)) {
                isSame = true;
                count = 0;
            }
        } else {
            replyTo = InternetAddress.toString(this.getFrom());
            if (replyTo.equalsIgnoreCase(from)) {
                isSame = true;
                count = 1;
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, count);
        return isSame;
    }

    /**
     * Feature 79 The number of encoded URLs (may contain characters in
     * languages other than English).
     *
     * Uses URLDecoder to check for encoded links, if the url doesn't match
     * UTF-8 charset, it is encoded. Using the Arraylist created from finding
     * all the URLs in the email body.
     *
     * @return @throws MessagingException
     * @throws java.io.UnsupportedEncodingException
     */
    public int countEncodedURLS() throws MessagingException, UnsupportedEncodingException {
        int count = 0;
        methodName = "countEncodedURLS";
        for (String l : links) {
            if (!l.equals(decode(l, "UTF-8"))) {
                count++;
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, count);
        return count;
    }

    /**
     * Feature 80 Indicates the importance of an email (1 = Low, 2 = Medium, 3 =
     * High). Finds the Importance header, if not null, parses the integer
     * value.
     *
     * @return @throws MessagingException
     */
    public int headerImportance() throws MessagingException {
        int count = 0;
        methodName = "headerImportance";
        Enumeration<Header> en = this.getAllHeaders();
        while (en.hasMoreElements()) {
            Header header = en.nextElement();
            if (header.getName().equalsIgnoreCase("Importance")) {
                count = parseInt(header.getValue());
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, count);
        return count;
    }

    /**
     * Feature 81 The number of URLs with percent encoding
     * https://en.wikipedia.org/wiki/Percent-encoding (i.e., contains “%XW%YZ,).
     *
     * Regex pattern source : https://rgxdb.com/r/48L3HPJP
     *
     * @return @throws MessagingException
     */
    public int countPercentEncodedURLs() throws MessagingException {
        int count = 0;
        methodName = "countPercentEncodedURLs";
        Pattern linkPat = compile("/^(?:[^%]|%[0-9A-Fa-f]{2})+$/",
                CASE_INSENSITIVE | DOTALL);
        for (String l : links) {
            Matcher match = linkPat.matcher(l);
            while (match.find()) {
                count++;
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, count);
        return count;
    }

    /**
     * Feature 82 Indicates whether the “Importance” header exists. Gets the
     * Importance Headers, if found, counts
     *
     * @return @throws MessagingException
     */
    public boolean checkExistsHdrImportance() throws MessagingException {
        methodName = "checkExistsHdrImportance";
        boolean exists = false;
        int ex = 0;
        String[] impHeader = this.getHeader("Importance");
        if (impHeader != null) {
            exists = true;
            ex = 1;
        }
        //add feature record to HashMap
        messageMap.put(methodName, ex);
        return exists;
    }

    /**
     * Feature 83 The number of attachments with no file extension found in the
     * email.
     *
     * @return @throws MessagingException
     * @throws IOException
     */
    public int countNoExtAttchmnt() throws MessagingException, IOException {
        int noext = 0;
        methodName = "countNoExtAttchmnt";
        contentType = message.getContentType();
        if (contentType.contains("multipart")) {
            //check if message includes attachments
            multipart = (MimeMultipart) message.getContent();
            //parse through each message bodypart
            for (int i = 0; i < multipart.getCount(); i++) {
                MimeBodyPart bodyPart = (MimeBodyPart) multipart.getBodyPart(i);
                //check if bodypart is an attachment
                if (ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition())
                        && !bodyPart.getFileName().contains(".")) {
                    noext++;
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, noext);
        return noext;
    }

    /**
     * Feature 84 The number of unrecognized “Content-Disposition” fields found
     * in the email’s body. Uses a list of known disposition. Compares the
     * dispositions founds in the email and counts any dispositions not found in
     * the list.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws java.io.IOException
     */
    public int countUnknwnDisp(MimePart part) throws MessagingException, IOException {
        methodName = "countUnknwnDisp";
        if(part.isMimeType("text/*") && part.getDisposition() != null){
            String disp = part.getDisposition();
                if (disp != null && !disp.equalsIgnoreCase("INLINE") 
                        || !disp.equalsIgnoreCase("ATTACHMENT")) {
                    value++;
                }
        } else {
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                value = 0;
                countUnknwnDisp((MimeBodyPart) mp.getBodyPart(i));
            }
        }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 85 The number of FTP URLs. Uses pattern/matcher to find FTP URLs,
     * this method needs to be tested more.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countFTPUrls(MimePart part) throws MessagingException, IOException {
        methodName = "countFTPUrls";
        Pattern linkPattern = compile("<a[^>]+href=[\\\"']?([\\\"'ftp>]+)[\\\\\\\"']?[^>]*>(.+?)<\\/a>",
                CASE_INSENSITIVE | DOTALL);

        if (part.isMimeType("text/html")) {
            String htmlContent = (String) part.getContent();
            Matcher match = linkPattern.matcher(htmlContent);
            if (match.find()) {
                if (match.group(1) != null) {
                    value++;
                }
            }
        } else {
            if (part.isMimeType("Multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    value = 0;
                    countFTPUrls((MimePart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 86 The number of non-English characters in URLs observed in the
     * email.
     *
     * @return @throws MessagingException
     */
    public int countNonEngChar() throws MessagingException {
        int count = 0;
        methodName = "countNonEngChar";
        String nonEngRegex = "[@_!#$%^&*()<>?/\\|}{~:]";
        //--------------------TO DO------------------------------/
        //add feature record to HashMap
        messageMap.put(methodName, count);
        return count;
    }

    /**
     * Feature 87 The number of abnormal links in HTML. We define abnormal as
     * transparent links with a text similar to the background. Such links are
     * invisible to the reader’s eye and used for usually for malicious
     * purposes.
     *
     * @return @throws MessagingException
     */
    public int countAbnormHtmlLinks() throws MessagingException {
        int count = 0;
        value = 0;
        methodName = "countAbnormHtmlLinks";
        //------------------TO DO ---------------------------/
        //add feature record to HashMap
        messageMap.put(methodName, count);
        return count;
    }

    /**
     * Feature 88 The number of “Content-Type: multipart/digest” fields found in
     * the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws java.io.IOException
     */
    public int countTypeDigest(MimePart part) throws MessagingException, IOException {
        methodName = "countTypeDigest";
        if (part.isMimeType("multipart/digest")) {
            value++;
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countTypeDigest((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 89 The number of “Content-Type: ...; charset = "cp-850 ## fields
     * found in the email’s body. Uses the content-type parameters to find the
     * charset.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countCharsetCp850(MimePart part) throws MessagingException, IOException {
        methodName = "countCharsetCp850";
        String charset;
        if (part.isMimeType("text/*") && part.getDisposition() == null) {
            ContentType ct = new ContentType(part.getContentType());
            charset = ct.getParameter("charset");
            if (charset.equalsIgnoreCase("cp-850")) {
                value++;
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countCharsetCp850((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 90 Indicates whether the header “Subject” exists in the email’s
     * header.
     *
     * @return @throws MessagingException
     */
    public boolean checkExistSubject() throws MessagingException {
        int count = 0;
        methodName = "checkExistSubject";
        subject = this.getSubject();
        if (subject != null) {
            checkSubject = true;
            count = 1;
        }
        //add feature record to HashMap
        messageMap.put(methodName, count);
        return checkSubject;
    }

    /**
     * Feature 91 The number of “Content-Type: multipart/byterange” fields found
     * in the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws java.io.IOException
     */
    public int countTypeByteRange(MimePart part) throws MessagingException, IOException {
        methodName = "countTypeByteRange";
        if (part.isMimeType("multipart/ByteRange")) {
            value++;
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countTypeByteRange((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 92 The number of “Content-Type: multipart/form-data” fields found
     * in the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws java.io.IOException
     */
    public int countTypeForm_data(MimePart part) throws MessagingException, IOException {
        methodName = "countTypeForm_data";
        if (part.isMimeType("multipart/form-data")) {
            value++;
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countTypeForm_data((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 93 The number of “Content-Type: multipart/parallel” fields found
     * in the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws java.io.IOException
     */
    public int countTypeParallel(MimePart part) throws MessagingException, IOException {
        methodName = "countTypeParallel";
        if (part.isMimeType("multipart/parallel")) {
            value++;
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countTypeParallel((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 94 The number of “Content-Type: multipart/x-mixed-replaced”
     * fields found in the email’s body.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws java.io.IOException
     */
    public int countTypeXMixedReplaced(MimePart part) throws MessagingException, IOException {
        methodName = "countTypeXMixedReplaced";
        if (part.isMimeType("multipart/x-mixed-replaced")) {
            value++;
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countTypeXMixedReplaced((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 95 The number of “Content-Type: ...; charset = " iso 8859 ##
     * fields found in the email’s body. Uses the content type parameter of
     * plain/html to find the charset value.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countCharsetIso_8859(MimePart part) throws MessagingException, IOException {
        methodName = "countCharsetIso_8859";
        String charset;
        if (part.isMimeType("text/*") && part.getDisposition() == null) {
            ContentType ct = new ContentType(part.getContentType());
            charset = ct.getParameter("charset");
            if (charset.equalsIgnoreCase("iso 8859")) {
                value++;
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countCharsetIso_8859((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 96 The number of Content-Type: ...; charset = "koi" fields found
     * in the email's body. Uses the content type parameter of plain/html to
     * find the charset value.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countCharsetKoi(MimePart part) throws MessagingException, IOException {
        methodName = "countCharsetKoi";
        String charset;
        if (part.isMimeType("text/*") && part.getDisposition() == null) {
            ContentType ct = new ContentType(part.getContentType());
            charset = ct.getParameter("charset");
            if (charset.equalsIgnoreCase("Koi")) {
                value++;
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countCharsetKoi((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 97 The number of “Content-Type: ...; charset = "windows-12 ##
     * fields found in the email’s body. Uses the content type parameter of
     * plain/html to find the charset value.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countCharsetWindows_12(MimePart part) throws MessagingException, IOException {
        methodName = "countCharsetWindows_12";
        String charset;
        if (part.isMimeType("text/*") && part.getDisposition() == null) {
            ContentType ct = new ContentType(part.getContentType());
            charset = ct.getParameter("charset");
            if (charset.equalsIgnoreCase("windows-12")) {
                value++;
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countCharsetWindows_12((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 98 The number of “Content-Type: ...; charset = "x-sjis” fields
     * found in the email’s body. Uses the content type parameter of plain/html
     * to find the charset value.
     *
     * @param part
     * @return
     * @throws MessagingException
     * @throws IOException
     */
    public int countCharsetX_sjis(MimePart part) throws MessagingException, IOException {
        methodName = "countCharsetX_sjis";
        String charset;
        if (part.isMimeType("text/*") && part.getDisposition() == null) {
            ContentType ct = new ContentType(part.getContentType());
            charset = ct.getParameter("charset");
            if (charset.equalsIgnoreCase("x-sjis")) {
                value++;
            }
        } else {
            if (part.isMimeType("multipart/*")) {
                Multipart mp = (Multipart) part.getContent();
                for (int i = 0; i < mp.getCount(); i++) {
                    countCharsetX_sjis((MimeBodyPart) mp.getBodyPart(i));
                }
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, value);
        return value;
    }

    /**
     * Feature 99 The number of recipients in the “Error-To” header. Retrieves
     * the Error-To header, if the header is found, counts recipients.
     *
     * @return
     * @throws MessagingException
     */
    public int countRecipientsInErrorTo() throws MessagingException {
        int count = 0;
        methodName = "countRecipientsInErrorTo";
        String[] errorHeader = this.getHeader("Error-To");
        if (errorHeader != null) {
            for (String rh : errorHeader) {
                count++;
            }
        }
        //add feature record to HashMap
        messageMap.put(methodName, count);
        return count;
    }

    /**
     * Feature 100 The number of unrecognized MimeMessage elements found in the
     * email.
     *
     * Uses the previous methods of finding unknown content types,
     * content-transfer-encoding, file extensions, etc and places into the
     * messageMap.
     *
     * @return
     * @throws MessagingException
     */
    public int countUnrecognizedMimeMessageElems() throws MessagingException {
        methodName = "countUnrecognizedMimeMessageElems";

        //add feature record to HashMap
        messageMap.put(methodName, notRecognized);
        return notRecognized;
    }

    /**
     *
     * @return
     */
    public HashMap<String, Integer> getMap() {
        return messageMap;
    }

    /**
     *
     */
    synchronized public void createCSV() {
        csvWriter = new PhPhCSVWriter();
        phPhWriter(messageMap);
    }

    /**
     *
     * @return
     */
    @Override
    public String toString() {
        str = messageMap.toString();
        return str;
    }

}
