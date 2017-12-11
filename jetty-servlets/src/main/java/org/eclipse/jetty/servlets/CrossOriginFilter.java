//
//  ========================================================================
//  Copyright (c) 1995-2017 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.servlets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * Implementation of the
 * <a href="http://www.w3.org/TR/cors/">cross-origin resource sharing</a>.
 * <p>
 * A typical example is to use this filter to allow cross-domain
 * <a href="http://cometd.org">cometd</a> communication using the standard
 * long polling transport instead of the JSONP transport (that is less
 * efficient and less reactive to failures).
 * <p>
 * This filter allows the following configuration parameters:
 * <dl>
 * <dt>allowedOrigins</dt>
 * <dd>a comma separated list of origins that are
 * allowed to access the resources. Default value is <b>*</b>, meaning all
 * origins.
 * <p>
 * If an allowed origin contains one or more * characters (for example
 * http://*.domain.com), then "*" characters are converted to ".*", "."
 * characters are escaped to "\." and the resulting allowed origin
 * interpreted as a regular expression.
 * <p>
 * Allowed origins can therefore be more complex expressions such as
 * https?://*.domain.[a-z]{3} that matches http or https, multiple subdomains
 * and any 3 letter top-level domain (.com, .net, .org, etc.).
 * <p>
 * Won't be used if <b>allowedOriginsRegex</b> is declared.
 * </dd>
 * <p>
 * <dt>allowedOriginsRegex</dt>
 * <dd>a new line separated list of origins regular expressions that are
 * allowed to access the resources. There is no default value, meaning only
 * <b>allowedOrigins</b> will match.
 * </dd>
 * <p>
 * <dt>allowedTimingOrigins</dt>
 * <dd>a comma separated list of origins that are
 * allowed to time the resource. Default value is the empty string, meaning
 * no origins.
 * <p>
 * The check whether the timing header is set, will be performed only if
 * the user gets general access to the resource using the <b>allowedOrigins</b>.
 * <p>
 * Won't be used if <b>allowedTimingOriginsRegex</b> is declared.
 * </dd>
 * <dt>allowedTimingOriginsRegex</dt>
 * <dd>a new line separated list of origins regular expressions that are
 * allowed to time the resource. There is no default value, meaning only
 * <b>allowedTimingOrigins</b> will match.
 * </dd>
 * <p>
 * <dt>allowedMethods</dt>
 * <dd>a comma separated list of HTTP methods that
 * are allowed to be used when accessing the resources. Default value is
 * <b>GET,POST,HEAD</b></dd>
 * 
 * 
 * <dt>allowedHeaders</dt>
 * <dd>a comma separated list of HTTP headers that
 * are allowed to be specified when accessing the resources. Default value
 * is <b>X-Requested-With,Content-Type,Accept,Origin</b>. If the value is a single "*",
 * this means that any headers will be accepted.</dd>
 * 
 * <dt>preflightMaxAge</dt>
 * <dd>the number of seconds that preflight requests
 * can be cached by the client. Default value is <b>1800</b> seconds, or 30
 * minutes</dd>
 * 
 * <dt>allowCredentials</dt>
 * <dd>a boolean indicating if the resource allows
 * requests with credentials. Default value is <b>true</b></dd>
 * 
 * <dt>exposedHeaders</dt>
 * <dd>a comma separated list of HTTP headers that
 * are allowed to be exposed on the client. Default value is the
 * <b>empty list</b></dd>
 * 
 * <dt>chainPreflight</dt>
 * <dd>if true preflight requests are chained to their
 * target resource for normal handling (as an OPTION request).  Otherwise the
 * filter will response to the preflight. Default is <b>true</b>.</dd>
 * 
 * </dl>
 * A typical configuration could be:
 * <pre>
 * &lt;web-app ...&gt;
 *     ...
 *     &lt;filter&gt;
 *         &lt;filter-name&gt;cross-origin&lt;/filter-name&gt;
 *         &lt;filter-class&gt;org.eclipse.jetty.servlets.CrossOriginFilter&lt;/filter-class&gt;
 *     &lt;/filter&gt;
 *     &lt;filter-mapping&gt;
 *         &lt;filter-name&gt;cross-origin&lt;/filter-name&gt;
 *         &lt;url-pattern&gt;/cometd/*&lt;/url-pattern&gt;
 *     &lt;/filter-mapping&gt;
 *     ...
 * &lt;/web-app&gt;
 * </pre>
 */
public class CrossOriginFilter implements Filter
{
    private static final Logger LOG = Log.getLogger(CrossOriginFilter.class);

    // Request headers
    private static final String ORIGIN_HEADER = "Origin";
    public static final String ACCESS_CONTROL_REQUEST_METHOD_HEADER = "Access-Control-Request-Method";
    public static final String ACCESS_CONTROL_REQUEST_HEADERS_HEADER = "Access-Control-Request-Headers";
    // Response headers
    public static final String ACCESS_CONTROL_ALLOW_ORIGIN_HEADER = "Access-Control-Allow-Origin";
    public static final String ACCESS_CONTROL_ALLOW_METHODS_HEADER = "Access-Control-Allow-Methods";
    public static final String ACCESS_CONTROL_ALLOW_HEADERS_HEADER = "Access-Control-Allow-Headers";
    public static final String ACCESS_CONTROL_MAX_AGE_HEADER = "Access-Control-Max-Age";
    public static final String ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER = "Access-Control-Allow-Credentials";
    public static final String ACCESS_CONTROL_EXPOSE_HEADERS_HEADER = "Access-Control-Expose-Headers";
    public static final String TIMING_ALLOW_ORIGIN_HEADER = "Timing-Allow-Origin";
    // Implementation constants
    public static final String ALLOWED_ORIGINS_PARAM = "allowedOrigins";
    public static final String ALLOWED_ORIGINS_REGEX_PARAM = "allowedOriginsRegex";
    public static final String ALLOWED_TIMING_ORIGINS_PARAM = "allowedTimingOrigins";
    public static final String ALLOWED_TIMING_ORIGINS_REGEX_PARAM = "allowedTimingOriginsRegex";
    public static final String ALLOWED_METHODS_PARAM = "allowedMethods";
    public static final String ALLOWED_HEADERS_PARAM = "allowedHeaders";
    public static final String PREFLIGHT_MAX_AGE_PARAM = "preflightMaxAge";
    public static final String ALLOW_CREDENTIALS_PARAM = "allowCredentials";
    public static final String EXPOSED_HEADERS_PARAM = "exposedHeaders";
    public static final String OLD_CHAIN_PREFLIGHT_PARAM = "forwardPreflight";
    public static final String CHAIN_PREFLIGHT_PARAM = "chainPreflight";
    private static final String ANY_ORIGIN = "*";
    private static final String DEFAULT_ALLOWED_ORIGINS = "*";
    private static final String DEFAULT_ALLOWED_TIMING_ORIGINS = "";
    private static final List<String> SIMPLE_HTTP_METHODS = Arrays.asList("GET", "POST", "HEAD");
    private static final List<String> DEFAULT_ALLOWED_METHODS = Arrays.asList("GET", "POST", "HEAD");
    private static final List<String> DEFAULT_ALLOWED_HEADERS = Arrays.asList("X-Requested-With", "Content-Type", "Accept", "Origin");

    private boolean anyOriginAllowed;
    private boolean anyTimingOriginAllowed;
    private boolean anyHeadersAllowed;
    private List<Pattern> allowedOrigins = new ArrayList<Pattern>();
    private List<Pattern> allowedTimingOrigins = new ArrayList<Pattern>();
    private List<String> allowedMethods = new ArrayList<String>();
    private List<String> allowedHeaders = new ArrayList<String>();
    private List<String> exposedHeaders = new ArrayList<String>();
    private int preflightMaxAge;
    private boolean allowCredentials;
    private boolean chainPreflight;

    public void init(FilterConfig config) throws ServletException
    {
        String allowedOriginsConfig = config.getInitParameter(ALLOWED_ORIGINS_PARAM);
        String allowedTimingOriginsConfig = config.getInitParameter(ALLOWED_TIMING_ORIGINS_PARAM);
        
        generateAllowedOriginsFromRegex(allowedOrigins, config.getInitParameter(ALLOWED_ORIGINS_REGEX_PARAM));
        if(allowedOrigins.isEmpty())
            anyOriginAllowed = generateAllowedOrigins(allowedOrigins, allowedOriginsConfig, DEFAULT_ALLOWED_ORIGINS);
        generateAllowedOriginsFromRegex(allowedTimingOrigins, config.getInitParameter(ALLOWED_TIMING_ORIGINS_REGEX_PARAM));
        if(allowedTimingOrigins.isEmpty())
            anyTimingOriginAllowed = generateAllowedOrigins(allowedTimingOrigins, allowedTimingOriginsConfig, DEFAULT_ALLOWED_TIMING_ORIGINS);
        
        String allowedMethodsConfig = config.getInitParameter(ALLOWED_METHODS_PARAM);
        if (allowedMethodsConfig == null)
            allowedMethods.addAll(DEFAULT_ALLOWED_METHODS);
        else
            allowedMethods.addAll(Arrays.asList(StringUtil.csvSplit(allowedMethodsConfig)));

        String allowedHeadersConfig = config.getInitParameter(ALLOWED_HEADERS_PARAM);
        if (allowedHeadersConfig == null)
            allowedHeaders.addAll(DEFAULT_ALLOWED_HEADERS);
        else if ("*".equals(allowedHeadersConfig))
            anyHeadersAllowed = true;
        else
            allowedHeaders.addAll(Arrays.asList(StringUtil.csvSplit(allowedHeadersConfig)));

        String preflightMaxAgeConfig = config.getInitParameter(PREFLIGHT_MAX_AGE_PARAM);
        if (preflightMaxAgeConfig == null)
            preflightMaxAgeConfig = "1800"; // Default is 30 minutes
        try
        {
            preflightMaxAge = Integer.parseInt(preflightMaxAgeConfig);
        }
        catch (NumberFormatException x)
        {
            LOG.info("Cross-origin filter, could not parse '{}' parameter as integer: {}", PREFLIGHT_MAX_AGE_PARAM, preflightMaxAgeConfig);
        }

        String allowedCredentialsConfig = config.getInitParameter(ALLOW_CREDENTIALS_PARAM);
        if (allowedCredentialsConfig == null)
            allowedCredentialsConfig = "true";
        allowCredentials = Boolean.parseBoolean(allowedCredentialsConfig);

        String exposedHeadersConfig = config.getInitParameter(EXPOSED_HEADERS_PARAM);
        if (exposedHeadersConfig == null)
            exposedHeadersConfig = "";
        exposedHeaders.addAll(Arrays.asList(StringUtil.csvSplit(exposedHeadersConfig)));

        String chainPreflightConfig = config.getInitParameter(OLD_CHAIN_PREFLIGHT_PARAM);
        if (chainPreflightConfig != null)
            LOG.warn("DEPRECATED CONFIGURATION: Use " + CHAIN_PREFLIGHT_PARAM + " instead of " + OLD_CHAIN_PREFLIGHT_PARAM);
        else
            chainPreflightConfig = config.getInitParameter(CHAIN_PREFLIGHT_PARAM);
        if (chainPreflightConfig == null)
            chainPreflightConfig = "true";
        chainPreflight = Boolean.parseBoolean(chainPreflightConfig);

        if (LOG.isDebugEnabled())
        {
            LOG.debug("Cross-origin filter configuration: " +
                            ALLOWED_ORIGINS_PARAM + " = " + allowedOriginsConfig + ", " +
                            ALLOWED_TIMING_ORIGINS_PARAM + " = " + allowedTimingOriginsConfig + ", " +
                            ALLOWED_METHODS_PARAM + " = " + allowedMethodsConfig + ", " +
                            ALLOWED_HEADERS_PARAM + " = " + allowedHeadersConfig + ", " +
                            PREFLIGHT_MAX_AGE_PARAM + " = " + preflightMaxAgeConfig + ", " +
                            ALLOW_CREDENTIALS_PARAM + " = " + allowedCredentialsConfig + "," +
                            EXPOSED_HEADERS_PARAM + " = " + exposedHeadersConfig + "," +
                            CHAIN_PREFLIGHT_PARAM + " = " + chainPreflightConfig
            );
        }
    }

    static boolean generateAllowedOrigins(List<Pattern> allowedOriginStore, String allowedOriginsConfig, String defaultOrigin) 
    {
        if (allowedOriginsConfig == null)
            allowedOriginsConfig = defaultOrigin;
        String[] allowedOrigins = StringUtil.csvSplit(allowedOriginsConfig);
        for (String allowedOrigin : allowedOrigins)
        {
            if (allowedOrigin.length() > 0)
            {
                if (ANY_ORIGIN.equals(allowedOrigin))
                {
                    allowedOriginStore.clear();
                    return true;
                }
                else
                {
                    final String s = allowedOrigin.contains("*")
                                    ? parseAllowedWildcardOriginToRegex(allowedOrigin)
                                    : Pattern.quote(allowedOrigin)
                                    ;
                    allowedOriginStore.add(Pattern.compile(s));
                }
            }
        }
        return false;
    }
    
    static void generateAllowedOriginsFromRegex(final List<Pattern> allowedOriginStore, final String allowedOriginsConfigRegex) 
    {
        if (allowedOriginsConfigRegex == null)
            return;
        
        for (String allowedOrigin : allowedOriginsConfigRegex.split("\n"))
        {
            allowedOrigin = allowedOrigin.trim();
            if (allowedOrigin.length() > 0)
            {
                allowedOriginStore.add(Pattern.compile(allowedOrigin));
            }
        }
    }
    
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
    {
        handle((HttpServletRequest)request, (HttpServletResponse)response, chain);
    }

    private void handle(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException
    {
        String origin = request.getHeader(ORIGIN_HEADER);
        // Is it a cross origin request ?
        if (origin != null && isEnabled(request))
        {
            if (anyOriginAllowed || originMatches(allowedOrigins, origin))
            {
                if (isSimpleRequest(request))
                {
                    LOG.debug("Cross-origin request to {} is a simple cross-origin request", request.getRequestURI());
                    handleSimpleResponse(request, response, origin);
                }
                else if (isPreflightRequest(request))
                {
                    LOG.debug("Cross-origin request to {} is a preflight cross-origin request", request.getRequestURI());
                    handlePreflightResponse(request, response, origin);
                    if (chainPreflight)
                        LOG.debug("Preflight cross-origin request to {} forwarded to application", request.getRequestURI());
                    else
                        return;
                }
                else
                {
                    LOG.debug("Cross-origin request to {} is a non-simple cross-origin request", request.getRequestURI());
                    handleSimpleResponse(request, response, origin);
                }

                if (anyTimingOriginAllowed || originMatches(allowedTimingOrigins, origin))
                {
                    response.setHeader(TIMING_ALLOW_ORIGIN_HEADER, origin);
                }
                else
                {
                    LOG.debug("Cross-origin request to " + request.getRequestURI() + " with origin " + origin + " does not match allowed timing origins " + allowedTimingOrigins);
                }
            }
            else
            {
                LOG.debug("Cross-origin request to " + request.getRequestURI() + " with origin " + origin + " does not match allowed origins " + allowedOrigins);
            }
        }

        chain.doFilter(request, response);
    }

    protected boolean isEnabled(HttpServletRequest request)
    {
        // WebSocket clients such as Chrome 5 implement a version of the WebSocket
        // protocol that does not accept extra response headers on the upgrade response
        for (Enumeration<String> connections = request.getHeaders("Connection"); connections.hasMoreElements();)
        {
            String connection = (String)connections.nextElement();
            if ("Upgrade".equalsIgnoreCase(connection))
            {
                for (Enumeration<String>  upgrades = request.getHeaders("Upgrade"); upgrades.hasMoreElements();)
                {
                    String upgrade = (String)upgrades.nextElement();
                    if ("WebSocket".equalsIgnoreCase(upgrade))
                        return false;
                }
            }
        }
        return true;
    }

    static boolean originMatches(final List<Pattern> allowedOrigins, final String originList)
    {
        if (originList.trim().length() == 0)
            return false;

        String[] origins = originList.split(" ");
        for (String origin : origins)
        {
            if (origin.trim().length() == 0)
                continue;

            for (final Pattern allowedOrigin : allowedOrigins)
            {
                if (allowedOrigin.matcher(origin).matches())
                    return true;
            }
        }
        return false;
    }


    private static String parseAllowedWildcardOriginToRegex(String allowedOrigin)
    {
        String regex = allowedOrigin.replace(".", "\\.");
        return regex.replace("*", ".*"); // we want to be greedy here to match multiple subdomains, thus we use .*
    }

    private boolean isSimpleRequest(HttpServletRequest request)
    {
        String method = request.getMethod();
        if (SIMPLE_HTTP_METHODS.contains(method))
        {
            // TODO: implement better detection of simple headers
            // The specification says that for a request to be simple, custom request headers must be simple.
            // Here for simplicity I just check if there is a Access-Control-Request-Method header,
            // which is required for preflight requests
            return request.getHeader(ACCESS_CONTROL_REQUEST_METHOD_HEADER) == null;
        }
        return false;
    }

    private boolean isPreflightRequest(HttpServletRequest request)
    {
        String method = request.getMethod();
        if (!"OPTIONS".equalsIgnoreCase(method))
            return false;
        if (request.getHeader(ACCESS_CONTROL_REQUEST_METHOD_HEADER) == null)
            return false;
        return true;
    }

    private void handleSimpleResponse(HttpServletRequest request, HttpServletResponse response, String origin)
    {
        response.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, origin);
        //W3C CORS spec http://www.w3.org/TR/cors/#resource-implementation
        if (!anyOriginAllowed)
            response.addHeader("Vary", ORIGIN_HEADER);
        if (allowCredentials)
            response.setHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, "true");
        if (!exposedHeaders.isEmpty())
            response.setHeader(ACCESS_CONTROL_EXPOSE_HEADERS_HEADER, commify(exposedHeaders));
    }

    private void handlePreflightResponse(HttpServletRequest request, HttpServletResponse response, String origin)
    {
        boolean methodAllowed = isMethodAllowed(request);

        if (!methodAllowed)
            return;
        List<String> headersRequested = getAccessControlRequestHeaders(request);
        boolean headersAllowed = areHeadersAllowed(headersRequested);
        if (!headersAllowed)
            return;
        response.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, origin);
        //W3C CORS spec http://www.w3.org/TR/cors/#resource-implementation
        if (!anyOriginAllowed)
            response.addHeader("Vary", ORIGIN_HEADER);
        if (allowCredentials)
            response.setHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS_HEADER, "true");
        if (preflightMaxAge > 0)
            response.setHeader(ACCESS_CONTROL_MAX_AGE_HEADER, String.valueOf(preflightMaxAge));
        response.setHeader(ACCESS_CONTROL_ALLOW_METHODS_HEADER, commify(allowedMethods));
        if (anyHeadersAllowed)
            response.setHeader(ACCESS_CONTROL_ALLOW_HEADERS_HEADER, commify(headersRequested));
        else
            response.setHeader(ACCESS_CONTROL_ALLOW_HEADERS_HEADER, commify(allowedHeaders));
    }

    private boolean isMethodAllowed(HttpServletRequest request)
    {
        String accessControlRequestMethod = request.getHeader(ACCESS_CONTROL_REQUEST_METHOD_HEADER);
        LOG.debug("{} is {}", ACCESS_CONTROL_REQUEST_METHOD_HEADER, accessControlRequestMethod);
        boolean result = false;
        if (accessControlRequestMethod != null)
            result = allowedMethods.contains(accessControlRequestMethod);
        LOG.debug("Method {} is" + (result ? "" : " not") + " among allowed methods {}", accessControlRequestMethod, allowedMethods);
        return result;
    }

    private List<String> getAccessControlRequestHeaders(HttpServletRequest request)
    {
        String accessControlRequestHeaders = request.getHeader(ACCESS_CONTROL_REQUEST_HEADERS_HEADER);
        LOG.debug("{} is {}", ACCESS_CONTROL_REQUEST_HEADERS_HEADER, accessControlRequestHeaders);
        if (accessControlRequestHeaders == null)
            return Collections.emptyList();

        List<String> requestedHeaders = new ArrayList<String>();
        String[] headers = StringUtil.csvSplit(accessControlRequestHeaders);
        for (String header : headers)
        {
            String h = header.trim();
            if (h.length() > 0)
                requestedHeaders.add(h);
        }
        return requestedHeaders;
    }

    private boolean areHeadersAllowed(List<String> requestedHeaders)
    {
        if (anyHeadersAllowed)
        {
            LOG.debug("Any header is allowed");
            return true;
        }

        boolean result = true;
        for (String requestedHeader : requestedHeaders)
        {
            boolean headerAllowed = false;
            for (String allowedHeader : allowedHeaders)
            {
                if (requestedHeader.equalsIgnoreCase(allowedHeader.trim()))
                {
                    headerAllowed = true;
                    break;
                }
            }
            if (!headerAllowed)
            {
                result = false;
                break;
            }
        }
        LOG.debug("Headers [{}] are" + (result ? "" : " not") + " among allowed headers {}", requestedHeaders, allowedHeaders);
        return result;
    }

    private String commify(List<String> strings)
    {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < strings.size(); ++i)
        {
            if (i > 0) builder.append(",");
            String string = strings.get(i);
            builder.append(string);
        }
        return builder.toString();
    }

    public void destroy()
    {
        anyOriginAllowed = false;
        allowedOrigins.clear();
        allowedMethods.clear();
        allowedHeaders.clear();
        preflightMaxAge = 0;
        allowCredentials = false;
    }
}
