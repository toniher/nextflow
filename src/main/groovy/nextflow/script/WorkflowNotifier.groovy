/*
 * Copyright (c) 2013-2018, Centre for Genomic Regulation (CRG).
 * Copyright (c) 2013-2018, Paolo Di Tommaso and the respective authors.
 *
 *   This file is part of 'Nextflow'.
 *
 *   Nextflow is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Nextflow is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Nextflow.  If not, see <http://www.gnu.org/licenses/>.
 */

package nextflow.script
import java.nio.file.Path

import groovy.text.GStringTemplateEngine
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import nextflow.mail.Attachment
import nextflow.mail.Mail
import nextflow.mail.Mailer
/**
 * Send workflow completion notification
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class WorkflowNotifier {

    /**
     * A map representing the nextflow configuration
     */
    @PackageScope Map config

    /**
    * A map representing the variables defined in the script golab scope
     */
    @PackageScope Map variables

    /**
     * The {@link WorkflowMetadata} object
     */
    @PackageScope WorkflowMetadata workflow

    /**
     * Send notification email
     *
     * @param config A {@link Map} representing the nextflow configuration object
     */
    void sendNotification() {

        // fetch the `notification` configuration map defined in the config file
        def notification = (Map)config.notification
        if (!notification || !notification.enabled) {
            return
        }

        if (!notification.to) {
            log.warn "Missing notification email target recipients -- Notification is ignored"
            return
        }

        def mail = createMail(notification)
        def mailer = createMailer( (Map)config.mail )
        mailer.send(mail)
    }

    /**
     * Creates {@link Mailer} object that sends the actual email message
     *
     * @param config The {@link Mailer} settings correspoding to the content of the {@code mail} configuration file scope
     * @return A {@link Mailer} object
     */
    protected Mailer createMailer(Map config) {
        def mailer = new Mailer()
        mailer.config = config
        return mailer
    }

    /**
     * Create notification {@link nextflow.mail.Mail} object given the user parameters
     *
     * @param notification
     *      The user  provided notification parameters
     *      - to: one or more comma separate notification recipient email address
     *      - from: the sender email address
     *      - template: template file path, multiple templates can be provided by using a list object
     *      - binding: user provided map representing the variables used in the template
     * @return
     */
    protected Mail createMail(Map notification) {

        def mail = new Mail()
        // -- the subject
        mail.subject("Workflow completion [${workflow.runName}] - ${workflow.success ? 'SUCCEED' : 'FAILED'}")
        // -- the sender
        if (notification.from)
            mail.from(notification.from.toString())
        // -- the recipient(s)
        if (notification.to)
            mail.to(notification.to.toString())

        // -- load user template(s)
        List<File> templates = []
        normaliseTemplate0(notification.template, templates)
        if (templates) {
            final binding = normaliseBindings0(notification.binding)

            templates.each { file ->
                def content = loadMailTemplate(file, binding)
                def plain = file.extension == 'txt'
                if (plain) {
                    mail.text(content)
                } else {
                    mail.body(content)
                }
            }
        }
        // fallback on default embedded template
        else {
            mail.text(loadDefaultTextTemplate())
            mail.body(loadDefaultHtmlTemplate())
            mail.attach(loadDefaultLogo())
        }

        return mail
    }

    protected Map normaliseBindings0(binding) {

        if (binding == null)
            return null

        if (binding instanceof Map)
            return binding

        throw new IllegalArgumentException("Not a valid binding object [${binding.class.name}]: $binding")
    }

    protected List<File> normaliseTemplate0(entry, List<File> result) {
        if (entry == null)
            return result

        if (entry instanceof File) {
            result << (File) entry
            return result
        }

        if (entry instanceof Path) {
            result << entry.toFile()
            return result
        }

        if (entry instanceof String || entry instanceof GString) {
            result << new File(entry.toString())
            return result
        }

        if (entry instanceof List) {
            entry.each { normaliseTemplate0(it, result) }
            return result
        }

        throw new IllegalArgumentException("Not a valid template file type [${entry.getClass().getName()}]: $entry")
    }

    /**
     * Load notification email template file
     *
     * @return A string representing the email template
     */
    protected String loadMailTemplate(File file, Map binding) {

        if (file && !file.exists())
            throw new IllegalArgumentException("Notification template file does not exist -- check path: $file")

        loadMailTemplate0(file.newInputStream(), binding)
    }

    /**
     * Load and resolve default text email template
     *
     * @return Resolved text template string
     */
    protected String loadDefaultTextTemplate() {
        loadDefaultTemplate0('/nextflow/mail/notification.txt')
    }

    /**
     * Load and resolve default HTML email template
     *
     * @return Resolved HTML template string
     */
    protected String loadDefaultHtmlTemplate() {
        loadDefaultTemplate0('/nextflow/mail/notification.html')
    }

    /**
     * Load the HTML email logo attachment
     * @return A {@link Attachment} object representing the image logo to be included in the HTML email
     */
    protected Attachment loadDefaultLogo() {
        Attachment.resource('/nextflow/mail/nextflow200x40.png', contentId: '<nxf-logo>', disposition: 'inline')
    }

    private String loadDefaultTemplate0(String classpathResource) {
        def source = this.class.getResourceAsStream(classpathResource)
        if (!source)
            throw new IllegalArgumentException("Cannot load notification default template -- check classpath resource: $classpathResource")
        loadMailTemplate0(source, [:])
    }

    private String loadMailTemplate0(InputStream source, Map binding) {
        def map = new HashMap()
        map.putAll(variables)
        map.putAll(binding)

        def template = new GStringTemplateEngine().createTemplate(new InputStreamReader(source))
        template.make(map).toString()
    }

}
