/*******************************************************************************
 * Copyright (c) 2010 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Benoit Jacquemoud - initial API and implementation
 ******************************************************************************/

package org.weasis.servlet;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SLwebstart_launcher extends HttpServlet {
    private static final long serialVersionUID = 8946852726380985736L;
    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(SLwebstart_launcher.class);

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////

    protected static String QUERY_PARM_ARGUMENT = "arg";
    protected static String QUERY_PARM_PROPERTY = "pro";
    protected static String QUERY_PARM_CODEBASE = "cdb";
    protected static String QUERY_PARM_SOURCE = "src";

    protected static String JNLP_TAG_ELT_RESOURCES = "resources";
    protected static String JNLP_TAG_ELT_APPLICATION_DESC = "application-desc";
    protected static String JNLP_TAG_ATT_ARGUMENT = "argument";
    protected static String JNLP_TAG_ELT_PROPERTY = "property";
    protected static String JNLP_TAG_ATT_NAME = "name";
    protected static String JNLP_TAG_ATT_VALUE = "value";
    protected static String JNLP_TAG_ELT_ROOT = "jnlp";
    protected static String JNLP_TAG_ATT_CODEBASE = "codebase";
    protected static String JNLP_TAG_ATT_HREF = "href";

    static final String DEFAULT_JNLP_TEMPLATE_NAME = "launcher.jnlp";
    static final String JNLP_EXTENSION = ".jnlp";
    static final String JNLP_MIME_TYPE = "application/x-java-jnlp-file";

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    protected RequestDispatcher defaultRequestDispatcher;

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Constructor of the object.
     */
    public SLwebstart_launcher() {
        super();
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Initialization of the servlet. <br>
     * 
     * @throws ServletException
     *             if an error occurs
     */
    @Override
    public void init() throws ServletException {
        logger.debug("init() - getServletContext : {} ", getServletConfig().getServletContext());
        logger.debug("init() - getRealPath : {}", getServletConfig().getServletContext().getRealPath("/"));

        defaultRequestDispatcher = getServletConfig().getServletContext().getNamedDispatcher("default");
        logger.debug("init() - defaultRequestDispatcher : {}", defaultRequestDispatcher.getClass());
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * The doGet method of the servlet. <br>
     * 
     * This method is called when a form has its tag value method equals to get.
     * 
     * @param request
     *            the request send by the client to the server
     * @param response
     *            the response send by the server to the client
     * @throws ServletErrorException
     * @throws IOException
     * @throws ServletException
     * @throws ServletException
     *             if an error occurred
     * @throws IOException
     *             if an error occurred
     */

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        try {
            logRequestInfo(request);
            if (handleRequestAndRedirect(request, response)) {
                return;
            }

            JnlpTemplate launcher = locateLauncherTemplate(request);
            readLauncherTemplate(launcher);
            parseLauncherTemplate(launcher);
            String launcherStr = buildJnlpResponse(launcher, request);

            logger.debug("doGet(HttpServletRequest, HttpServletResponse) - launcherStr = [\n{}\n]", launcherStr);

            response.setContentType(JNLP_MIME_TYPE);
            response.setContentLength(launcherStr.length());

            PrintWriter outWriter = response.getWriter();
            outWriter.println(launcherStr);
            outWriter.close();

        } catch (ServletErrorException e) {
            logger.error("doGet(HttpServletRequest, HttpServletResponse)", e);
            response.sendError(e.responseErrorCode);
        } catch (Exception e) {
            logger.error("doGet(HttpServletRequest, HttpServletResponse)", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * The doPost method of the servlet. <br>
     * 
     * This method is called when a form has its tag value method equals to post.
     * 
     * @param request
     *            the request send by the client to the server
     * @param response
     *            the response send by the server to the client
     * @throws ServletException
     *             if an error occurred
     * @throws IOException
     *             if an error occurred
     */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doGet(request, response);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) throws IOException {

        try {
            if (handleRequestAndRedirect(request, response)) {
                return;
            }

            response.setContentType(JNLP_MIME_TYPE);

            // JnlpTemplate launcher = locateLauncherTemplate(request);
            // response.setContentLength(launcher.realPathURL.openConnection().getContentLength());
            // response.sendError(HttpServletResponse.SC_OK);
            // NOTE : getContentLength is not real content length till it's modified in doGet

        } catch (Exception e) {
            logger.error("doHead(HttpServletRequest, HttpServletResponse)", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * Destruction of the servlet. <br>
     */
    @Override
    public void destroy() {
        super.destroy();
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @param request
     */
    protected void logRequestInfo(HttpServletRequest request) {
        logger.debug("logRequestInfo(HttpServletRequest) - getRequestQueryURL : {}{}", request.getRequestURL()
            .toString(), request.getQueryString() != null ? ("?" + request.getQueryString().trim()) : "");
        logger.debug("logRequestInfo(HttpServletRequest) - getContextPath : {}", request.getContextPath());
        logger.debug("logRequestInfo(HttpServletRequest) - getRequestURI : {}", request.getRequestURI());
        logger.debug("logRequestInfo(HttpServletRequest) - getServletPath : {}", request.getServletPath());
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * @param request
     * @param response
     * @return
     * @throws ServletException
     * @throws IOException
     */
    protected boolean handleRequestAndRedirect(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

        // if (request.getServletPath().endsWith("/"))
        // request.getRequestDispatcher(DEFAULT_JNLP_TEMPLATE_NAME).forward(request, response);

        // NOTE : if redirect to default launcher template file the ?src= parameter would never be handle

        // NOTE : in web.xml config file the "<url-pattern>" must be directory mapping and not file mapping like
        // "*.jnlp" otherwise external launcher template from ?src= parameter would never be used and launcher template
        // could never be outside of Servlet context
        // Ex of mapping the root of Servlet context : <servlet-mapping><url-pattern>/</url-pattern></servlet-mapping>

        if (!request.getServletPath().endsWith("/") && !request.getServletPath().endsWith(JNLP_EXTENSION)) {
            logger
                .debug(
                    "handleRequestAndRedirect(HttpServletRequest, HttpServletResponse) - forward request to default dispatcher : {}",
                    request.getServletPath());
            defaultRequestDispatcher.forward(request, response);
            return true;
        }
        return false;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @param request
     * @return
     * @throws ServletErrorException
     */
    protected JnlpTemplate locateLauncherTemplate(HttpServletRequest request) throws ServletErrorException {

        String serverPath = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();

        String templatePath = null;
        String templateFileName = null;
        URL templateURL = null;
        String codeBasePath = null;

        try {
            // GET LAUNCHER TEMPLATE FILE NAME, PATH AND URL

            templatePath = serverPath + request.getContextPath() + request.getServletPath();

            String queryLauncherPath = request.getParameter(QUERY_PARM_SOURCE); // this overrides Servlet context path
            if (queryLauncherPath != null) { // template isn't in the Web Servlet Context
                if (queryLauncherPath.startsWith("/")) {
                    templatePath = serverPath + queryLauncherPath; // supposed to be "serverPath/URI"
                } else {
                    templatePath = queryLauncherPath; // supposed to be a new valid URL for launcher template
                }
            }

            if (templatePath.endsWith("/")) {
                templateFileName = DEFAULT_JNLP_TEMPLATE_NAME; // default value
            } else {
                int fileNameBeginIndex = templatePath.lastIndexOf("/") + 1;
                templateFileName = templatePath.substring(fileNameBeginIndex);
                templatePath = templatePath.substring(0, fileNameBeginIndex);
            }

            if (templatePath.endsWith("/")) {
                templatePath = templatePath.substring(0, templatePath.length() - 1);
            }

            if (templatePath.startsWith(serverPath + request.getContextPath())) {
                // !!!! templateURL = getServletContext().getResource(templatePath + "/" + templateFileName);
                // NOTE : resource has to be accessed through local File URL Connection otherwise the Servlet is
                // called again in loop trying reading the file

                String URItemplatePath = templatePath.replaceFirst(serverPath + request.getContextPath(), "");
                String realPath = getServletContext().getRealPath(URItemplatePath + templateFileName);
                templateURL = new File(realPath).toURL();

            } else {
                templateURL = new URL(templatePath + "/" + templateFileName);
            }

            logger.debug("locateLauncherTemplate(HttpServletRequest) - String templatePath = {}", templatePath);
            logger.debug("locateLauncherTemplate(HttpServletRequest) - String templateFileName = {}", templateFileName);
            logger.debug("locateLauncherTemplate(HttpServletRequest) - URL templateURL = {}", templateURL);

            // CHECK IF LAUNCHER TEMPLATE RESOURCE EXIST

            URLConnection launcherTemplateConnection = templateURL.openConnection();

            if (launcherTemplateConnection instanceof HttpURLConnection) {
                if (((HttpURLConnection) launcherTemplateConnection).getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new Exception("HttpURLConnection not accessible : " + templateURL);
                }
            } else if (launcherTemplateConnection.getContentLength() <= 0) {
                throw new Exception("URLConnection  not accessible : " + templatePath + "/" + templateFileName);
            }

            // GET SOURCECODE CODEBASE PATH

            String queryCodeBasePath = request.getParameter(QUERY_PARM_CODEBASE);
            if (queryCodeBasePath != null) { // codebase is not in the Web Servlet Context
                if (queryCodeBasePath.startsWith("/")) {
                    codeBasePath = serverPath + queryCodeBasePath;
                } else {
                    codeBasePath = queryCodeBasePath; // supposed to be a new valid URL for codeBase repo
                }
            } else {
                codeBasePath = templatePath; // default value
            }

            if (codeBasePath.endsWith("/")) {
                codeBasePath = codeBasePath.substring(0, codeBasePath.length() - 1);
            }

        } catch (Exception e) {
            throw new ServletErrorException(HttpServletResponse.SC_NOT_FOUND, "Can't locate launcher template", e);
        }

        return new JnlpTemplate(templateFileName, templatePath, templateURL, codeBasePath);
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    protected class JnlpTemplate {

        final String fileName;
        final String urlPath;
        final URL realPathURL;
        final String codeBasePath;

        Element rootElt;

        public JnlpTemplate(String fileName, String urlPath, URL realPathURL, String codeBasePath) {
            this.fileName = fileName;
            this.urlPath = urlPath;
            this.realPathURL = realPathURL;
            this.codeBasePath = codeBasePath;
        }
    }

    public class ServletErrorException extends Exception {
        private static final long serialVersionUID = -1673431720286835416L;

        int responseErrorCode;

        public ServletErrorException(int httpServletResponseCode, String message, Throwable cause) {
            super(message, cause);
            this.responseErrorCode = httpServletResponseCode;
        }

        public ServletErrorException(int httpServletResponseCode, String message) {
            super(message);
            this.responseErrorCode = httpServletResponseCode;
        }

        public ServletErrorException(int httpServletResponseCode) {
            super();
            this.responseErrorCode = httpServletResponseCode;
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * @param launcher
     * @throws ServletErrorException
     */
    protected void readLauncherTemplate(JnlpTemplate launcher) throws ServletErrorException {

        if (logger.isDebugEnabled()) {
            try {
                String launcherTemplateStr = "";

                ByteArrayOutputStream bufferOS = null;
                BufferedInputStream bufferIS = null;
                try {
                    bufferIS = new BufferedInputStream(launcher.realPathURL.openConnection().getInputStream());
                    bufferOS = new ByteArrayOutputStream();
                    int charRead;
                    while ((charRead = bufferIS.read()) != -1) {
                        bufferOS.write((byte) charRead);
                    }

                    // launcherTemplateStr = bufferOS.toString();
                    // NOTE : this way of reading doesn't take car of any specified charset encoding
                    launcherTemplateStr = bufferOS.toString("UTF-8");
                } finally {
                    if (bufferOS != null) {
                        bufferOS.close();
                    }
                    bufferOS = null;
                    if (bufferIS != null) {
                        bufferIS.close();
                    }
                    bufferIS = null;
                }

                // OTHER MEANS OF READING
                // BufferedReader reader = null;
                // try {
                // StringBuilder sb = new StringBuilder();
                // reader = new BufferedReader(new InputStreamReader(launcher.realPathURL.openConnection()
                // .getInputStream(), "UTF-8"));
                //
                // String line;
                // while ((line = reader.readLine()) != null)
                // sb.append(line).append("\r\n");
                //
                // launcherTemplateStr = sb.toString();
                // } finally {
                // if (reader != null)
                // reader.close();
                // reader = null;
                // }

                logger.debug("readLauncherTemplate(JnlpTemplate) - String launcherTemplateStr = {}",
                    launcherTemplateStr);

            } catch (Exception e) {
                throw new ServletErrorException(HttpServletResponse.SC_NOT_ACCEPTABLE, "Can't read launcher template",
                    e);
            }
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * @param launcher
     * @throws ServletErrorException
     * @throws IOException
     */
    protected void parseLauncherTemplate(JnlpTemplate launcher) throws ServletErrorException, IOException {
        // PARSE JNLP LAUNCHER AS JDOM
        Element rootElt = null;
        BufferedReader reader = null;

        try {
            reader =
                new BufferedReader(new InputStreamReader(launcher.realPathURL.openConnection().getInputStream(),
                    "UTF-8"));

            rootElt = new SAXBuilder(false).build(reader).getRootElement();

            // rootElt = new SAXBuilder().build(launcher.realPathURL).getRootElement();
            // NOTE : doesn't work with all URl like "file://///server/..."

        } catch (JDOMException e) {
            throw new ServletErrorException(HttpServletResponse.SC_NOT_ACCEPTABLE, "Can't parse launcher template", e);
        } finally {
            if (reader != null) {
                reader.close();
            }
            reader = null;
        }

        if (!rootElt.getName().equals(JNLP_TAG_ELT_ROOT)) {
            throw new ServletErrorException(HttpServletResponse.SC_NOT_ACCEPTABLE, "Invalid JNLP launcher template");
        }

        launcher.rootElt = rootElt;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * @param launcher
     * @param request
     * @return
     * @throws ServletErrorException
     */
    protected String buildJnlpResponse(JnlpTemplate launcher, HttpServletRequest request) throws ServletErrorException {

        launcher.rootElt.setAttribute(JNLP_TAG_ATT_CODEBASE, launcher.codeBasePath);
        // rootLauncherElt.setAttribute(JNLP_TAG_ATT_HREF, launcher.fileName);
        launcher.rootElt.removeAttribute(JNLP_TAG_ATT_HREF); // this tag has not to be used inside dynamic JNLP

        handleArgParameters(launcher, request.getParameterValues(QUERY_PARM_ARGUMENT));
        handlePropertyParameters(launcher, request.getParameterValues(QUERY_PARM_PROPERTY));

        modifyURLPropertyValue(launcher);

        String outputStr = null;
        try {
            Format format = Format.getPrettyFormat();
            // Converts native encodings to ASCII with escaped Unicode like (ô è é...), necessary for jnlp
            format.setEncoding("US-ASCII");
            XMLOutputter outputter = new XMLOutputter(format);

            // XMLOutputter outputter = new XMLOutputter();
            // outputter.setEncoding("US-ASCII");
            // NOTE : deprecated with new version of JDOM
            outputStr = outputter.outputString(launcher.rootElt);

        } catch (Exception e) {
            throw new ServletErrorException(HttpServletResponse.SC_NOT_MODIFIED, "Can't build Jnlp launcher ", e);
        }

        return outputStr;
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /**
     * @param launcher
     * @param paramValues
     * @throws ServletErrorException
     * @throws Exception
     */
    protected void handleArgParameters(JnlpTemplate launcher, Object paramValues) throws ServletErrorException {
        String[] strValues = (paramValues instanceof String[]) ? (String[]) paramValues : null;

        if (launcher != null && launcher.rootElt != null && strValues != null) {
            try {
                Element applicationDescElt = launcher.rootElt.getChild(JNLP_TAG_ELT_APPLICATION_DESC);
                if (applicationDescElt == null) {
                    throw new Exception("JNLP TAG : <" + JNLP_TAG_ELT_APPLICATION_DESC + "> is not found");
                }

                for (int i = 0; i < strValues.length; i++) {
                    applicationDescElt.addContent(new Element(JNLP_TAG_ATT_ARGUMENT).addContent(strValues[i]));
                }
            } catch (Exception e) {
                throw new ServletErrorException(HttpServletResponse.SC_NOT_ACCEPTABLE,
                    "Can't add argument parameter to launcher template ", e);
            }
        }
    }

    /**
     * 
     * @param launcher
     * @param paramValues
     * @throws ServletErrorException
     * @throws Exception
     */
    protected void handlePropertyParameters(JnlpTemplate launcher, Object paramValues) throws ServletErrorException {
        String[] strValues = (paramValues instanceof String[]) ? (String[]) paramValues : null;

        if (launcher != null && launcher.rootElt != null && strValues != null) {
            try {
                Element resourcesElt = launcher.rootElt.getChild(JNLP_TAG_ELT_RESOURCES);

                if (resourcesElt == null) {
                    throw new Exception("JNLP TAG : <" + JNLP_TAG_ELT_RESOURCES + "> is not found");
                }

                for (int i = 0; i < strValues.length; i++) {
                    // split any whitespace character: [ \t\n\x0B\f\r ]
                    String[] property = Pattern.compile("\\s").split(strValues[i], 2);

                    String propertyName = property != null && property.length > 0 ? property[0] : null;
                    String propertyValue = property != null && property.length > 1 ? property[1] : null;

                    if (propertyName != null && propertyValue != null) {
                        // if (propertyValue.startsWith("/"))
                        // propertyValue = launcher.codeBasePath + (launcher.codeBasePath.endsWith("/") ? "" : "/")
                        // + propertyValue;
                        Element propertyElt = new Element(JNLP_TAG_ELT_PROPERTY);
                        propertyElt.setAttribute(JNLP_TAG_ATT_NAME, propertyName);
                        propertyElt.setAttribute(JNLP_TAG_ATT_VALUE, propertyValue);

                        resourcesElt.addContent(propertyElt);
                    } else {
                        throw new Exception("Query Parameter {property} is invalid : " + strValues.toString());
                    }
                }
            } catch (Exception e) {
                throw new ServletErrorException(HttpServletResponse.SC_NOT_ACCEPTABLE,
                    "Can't add property parameter to launcher template", e);
            }
        }
    }

    /**
     * 
     * @param launcher
     * @throws ServletErrorException
     */
    protected void modifyURLPropertyValue(JnlpTemplate launcher) throws ServletErrorException {

        if (launcher != null && launcher.rootElt != null) {
            try {
                Element resourcesElt = launcher.rootElt.getChild(JNLP_TAG_ELT_RESOURCES);
                if (resourcesElt == null) {
                    throw new Exception("JNLP TAG : <" + JNLP_TAG_ELT_RESOURCES + "> is not found");
                }

                for (Element elt : (List<Element>) resourcesElt.getChildren(JNLP_TAG_ELT_PROPERTY)) {
                    String propertyValue = elt.getAttributeValue(JNLP_TAG_ATT_VALUE);
                    if (propertyValue != null && propertyValue.startsWith("${cdb}")) {
                        propertyValue = propertyValue.replace("${cdb}", launcher.codeBasePath);
                        elt.setAttribute(JNLP_TAG_ATT_VALUE, propertyValue);
                    }
                }
            } catch (Exception e) {
                throw new ServletErrorException(HttpServletResponse.SC_NOT_ACCEPTABLE,
                    "Can't modify URLProperty value in launcher template", e);
            }
        }
    }
}
