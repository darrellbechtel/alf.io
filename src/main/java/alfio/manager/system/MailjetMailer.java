/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.manager.system;

import alfio.model.Event;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.util.Json;
import com.squareup.okhttp.*;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Log4j2
public class MailjetMailer implements Mailer  {

    private final OkHttpClient client = new OkHttpClient();
    private final ConfigurationManager configurationManager;

    public MailjetMailer(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    @Override
    public void send(Event event, String to, String subject, String text, Optional<String> html, Attachment... attachment) {
        String apiKeyPublic = configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.MAILJET_APIKEY_PUBLIC));
        String apiKeyPrivate = configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.MAILJET_APIKEY_PRIVATE));

        String fromEmail = configurationManager.getRequiredValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.MAILJET_FROM));

        //https://dev.mailjet.com/guides/?shell#sending-with-attached-files
        Map<String, Object> mailPayload = new HashMap<>();

        mailPayload.put("FromEmail", fromEmail);
        mailPayload.put("FromName", event.getDisplayName());
        mailPayload.put("Subject", subject);
        mailPayload.put("Text-part", text);
        html.ifPresent(h -> mailPayload.put("Html-part", h));
        mailPayload.put("Recipients", Collections.singletonList(Collections.singletonMap("Email", to)));

        String replyTo = configurationManager.getStringConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.MAIL_REPLY_TO), "");
        if(StringUtils.isNotBlank(replyTo)) {
            mailPayload.put("Headers", Collections.singletonMap("Reply-To", replyTo));
        }

        if(attachment != null && attachment.length > 0) {
            mailPayload.put("Attachments", Arrays.stream(attachment).map(MailjetMailer::fromAttachment).collect(Collectors.toList()));
        }

        try {
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), Json.GSON.toJson(mailPayload));
            Request request = new Request.Builder().url("https://api.mailjet.com/v3/send")
                .header("Authorization", Credentials.basic(apiKeyPublic, apiKeyPrivate))
                .post(body)
                .build();

            Response resp = client.newCall(request).execute();
            if (!resp.isSuccessful()) {
                log.warn("sending email was not successful:" + resp);
            }

        } catch(IOException e) {
            log.warn("error while sending email", e);
        }


    }

    private static Map<String, String> fromAttachment(Attachment a) {
        Map<String, String> m = new HashMap<>();
        m.put("Content-type", a.getContentType());
        m.put("Filename", a.getFilename());
        m.put("content", Base64.encodeBase64String(a.getSource()));
        return m;
    }
}
