package com.veertu.ankaMgmtSdk;

import com.intellij.openapi.diagnostic.Logger;
import com.veertu.ankaMgmtSdk.exceptions.AnkaMgmtException;
import com.veertu.ankaMgmtSdk.exceptions.AnkaUnAuthenticatedRequestException;
import com.veertu.ankaMgmtSdk.exceptions.AnkaUnauthorizedRequestException;
import com.veertu.ankaMgmtSdk.exceptions.ClientException;
import jetbrains.buildServer.log.Loggers;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import javax.net.ssl.SSLContext;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.SSLException;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.net.URL;
import org.apache.http.client.utils.URIBuilder;

/**
 * Created by asafgur on 09/05/2017.
 */
public class AnkaMgmtCommunicator {


    protected URL mgmtUrl;
    protected final int timeout;
    protected final int maxRetries;
    protected boolean skipTLSVerification;
    protected String rootCA;
    protected static final Logger LOG = Logger.getInstance(Loggers.CLOUD_CATEGORY_ROOT);

    public AnkaMgmtCommunicator(String url) {
        this.maxRetries = 10;
        this.timeout = 4000;
        this.skipTLSVerification = false;
        try {
            LOG.info(String.format("url: %s", url));
            URL tmpUrl = new URL(url);
            URIBuilder b = new URIBuilder();
            b.setScheme(tmpUrl.getProtocol());
            b.setHost( tmpUrl.getHost());
            b.setPort(tmpUrl.getPort());
            mgmtUrl = b.build().toURL();

        } catch (IOException | URISyntaxException e) {
            LOG.info("Malformed or wrong url");
            e.printStackTrace();
        }
    }

    public AnkaMgmtCommunicator(String mgmtURL, boolean skipTLSVerification) {
        this(mgmtURL);
        this.skipTLSVerification = skipTLSVerification;

    }

    public AnkaMgmtCommunicator(String mgmtUrl, String rootCA) {
        this(mgmtUrl);
        this.rootCA = rootCA;
    }

    public AnkaMgmtCommunicator(String mgmtUrl, boolean skipTLSVerification, String rootCA) {
        this(mgmtUrl);
        this.skipTLSVerification = skipTLSVerification;
        this.rootCA = rootCA;
    }

    public List<AnkaVmTemplate> listTemplates() throws AnkaMgmtException {
        List<AnkaVmTemplate> templates = new ArrayList<AnkaVmTemplate>();
        String url = String.format("%s/api/v1/registry/vm", mgmtUrl.toString());
        LOG.info(String.format("List: %s", url));
        try {
            JSONObject jsonResponse = this.doRequest(RequestMethod.GET, url);
            String logicalResult = jsonResponse.getString("status");
            if (logicalResult.equals("OK")) {
                JSONArray vmsJson = jsonResponse.getJSONArray("body");
                for (Object j : vmsJson) {
                    JSONObject jsonObj = (JSONObject) j;
                    String vmId = jsonObj.getString("id");
                    String name = jsonObj.getString("name");
                    AnkaVmTemplate vm = new AnkaVmTemplate(vmId, name);
                    templates.add(vm);
                }
            }
        } catch (IOException e) {
            return templates;
        }
        return templates;
    }

    public List<String> getTemplateTags(String templateId) throws AnkaMgmtException {
        List<String> tags = new ArrayList<String>();
        String url = String.format("%s/api/v1/registry/vm?id=%s", mgmtUrl.toString(), templateId);
        try {
            JSONObject jsonResponse = this.doRequest(RequestMethod.GET, url);
            String logicalResult = jsonResponse.getString("status");
            if (logicalResult.equals("OK")) {
                JSONObject templateVm = jsonResponse.getJSONObject("body");
                JSONArray vmsJson = templateVm.getJSONArray("versions");
                for (Object j : vmsJson) {
                    JSONObject jsonObj = (JSONObject) j;
                    String tag = jsonObj.getString("tag");
                    tags.add(tag);
                }
            }
        } catch (IOException e) {
            System.out.printf("Exception trying to access: '%s'", url);
        } catch (org.json.JSONException e) {
            System.out.printf("Exception trying to parse response: '%s'", url);
        }
        return tags;
    }

    public List<NodeGroup> getNodeGroups() throws AnkaMgmtException {
        List<NodeGroup> groups = new ArrayList<>();
        String url = String.format("%s/api/v1/group", mgmtUrl.toString());
        try {
            JSONObject jsonResponse = this.doRequest(RequestMethod.GET, url);
            String logicalResponse = jsonResponse.getString("status");
            if (logicalResponse.equals("OK")) {
                JSONArray groupsJson = jsonResponse.getJSONArray("body");
                for (int i = 0; i < groupsJson.length(); i++) {
                    JSONObject groupJsonObject = groupsJson.getJSONObject(i);
                    NodeGroup nodeGroup = new NodeGroup(groupJsonObject);
                    groups.add(nodeGroup);
                }
            } else {
                throw new AnkaMgmtException(jsonResponse.getString("message"));
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new AnkaMgmtException(e);
        } catch (JSONException e) {
            return groups;
        }
        return groups;
    }

    public String startVm(String templateId, String tag, String nameTemplate, String startUpScript, String groupId, int priority) throws AnkaMgmtException {
        String url = String.format("%s/api/v1/vm", mgmtUrl.toString());
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("vmid", templateId);
        if (tag != null)
            jsonObject.put("tag", tag);
        if (nameTemplate != null)
            jsonObject.put("name_template", nameTemplate);
        if (startUpScript != null) {
            String b64Script = Base64.encodeBase64String(startUpScript.getBytes());
            jsonObject.put("startup_script", b64Script);
        }
        if (groupId != null) {
            jsonObject.put("group_id", groupId);
        }
        if (priority > 0) {
            jsonObject.put("priority", priority);
        }
        JSONObject jsonResponse = null;
        try {
            jsonResponse = this.doRequest(RequestMethod.POST, url, jsonObject);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        String logicalResult = jsonResponse.getString("status");
        if (logicalResult.equals("OK")) {
            JSONArray uuidsJson = jsonResponse.getJSONArray("body");
            if (uuidsJson.length() >= 1 ){
                return uuidsJson.getString(0);
            }

//            return jsonResponse.getString("body");
        }
        return null;
    }

    public AnkaVmSession showVm(String sessionId) throws AnkaMgmtException {
        String url = String.format("%s/api/v1/vm?id=%s", mgmtUrl.toString(), sessionId);
        try {
            JSONObject jsonResponse = this.doRequest(RequestMethod.GET, url);
            String logicalResult = jsonResponse.getString("status");
            if (logicalResult.equals("OK")) {
                JSONObject body = jsonResponse.getJSONObject("body");
                return new AnkaVmSession(sessionId, body);
            }
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean terminateVm(String sessionId) throws AnkaMgmtException {
        String url = String.format("%s/api/v1/vm", mgmtUrl.toString());
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("id", sessionId);
            JSONObject jsonResponse = this.doRequest(RequestMethod.DELETE, url, jsonObject);
            String logicalResult = jsonResponse.getString("status");
            if (logicalResult.equals("OK")) {
                return true;
            }
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }


    public List<AnkaVmSession> list() throws AnkaMgmtException {
        List<AnkaVmSession> vms = new ArrayList<>();
        String url = String.format("%s/api/v1/vm", mgmtUrl.toString());
        try {
            JSONObject jsonResponse = this.doRequest(RequestMethod.GET, url);
            String logicalResult = jsonResponse.getString("status");
            if (logicalResult.equals("OK")) {
                JSONArray vmsJson = jsonResponse.getJSONArray("body");
                for (int i = 0; i < vmsJson.length(); i++) {
                    JSONObject vmJson = vmsJson.getJSONObject(i);
                    String instanceId = vmJson.getString("instance_id");
                    JSONObject vm = vmJson.getJSONObject("vm");
                    vm.put("instance_id", instanceId);
                    vm.put("cr_time", vm.getString("cr_time"));
                    AnkaVmSession ankaVmSession = AnkaVmSession.makeAnkaVmSessionFromJson(vmJson);
                    vms.add(ankaVmSession);
                }
            }
            return vms;
        } catch (IOException e) {
            return vms;
        }
    }

    public AnkaCloudStatus status() throws AnkaMgmtException {
        String url = String.format("%s/api/v1/status", mgmtUrl.toString());
        try {
            JSONObject jsonResponse = this.doRequest(RequestMethod.GET, url);
            String logicalResult = jsonResponse.getString("status");
            if (logicalResult.equals("OK")) {
                JSONObject statusJson = jsonResponse.getJSONObject("body");
                return AnkaCloudStatus.fromJson(statusJson);
            }
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    protected enum RequestMethod {
        GET, POST, DELETE
    }

    protected JSONObject doRequest(RequestMethod method, String url) throws IOException, AnkaMgmtException {
        return doRequest(method, url, null);
    }

    protected JSONObject doRequest(RequestMethod method, String url, JSONObject requestBody) throws IOException, AnkaMgmtException {
        int retry = 0;
        while (true){
            try {
                retry++;

                CloseableHttpClient httpClient = makeHttpClient();
                HttpRequestBase request;
                try {
                    switch (method) {
                        case POST:
                            HttpPost postRequest = new HttpPost(url);
                            request = setBody(postRequest, requestBody);
                            break;
                        case DELETE:
                            HttpDeleteWithBody delRequest = new HttpDeleteWithBody(url);
                            request = setBody(delRequest, requestBody);
                            break;
                        case GET:
                            request = new HttpGet(url);
                            break;
                        default:
                            request = new HttpGet(url);
                            break;
                    }

                    HttpResponse response = httpClient.execute(request);
                    int responseCode = response.getStatusLine().getStatusCode();
                    if (responseCode == 401) {
                        throw new AnkaUnAuthenticatedRequestException("Authentication Required");
                    }
                    if (responseCode == 403) {
                        throw new AnkaUnauthorizedRequestException("Not authorized to perform this request");
                    }
                    if (responseCode >= 400) {
                        throw new ClientException(request.getMethod() + request.getURI().toString() + "Bad Request");
                    }

                    if (responseCode != 200) {
                        LOG.error(String.format("url: %s response: %s", url, response.toString()));
                        return null;
                    }
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        BufferedReader rd = new BufferedReader(
                                new InputStreamReader(entity.getContent()));
                        StringBuffer result = new StringBuffer();
                        String line = "";
                        while ((line = rd.readLine()) != null) {
                            result.append(line);
                        }
                        JSONObject jsonResponse = new JSONObject(result.toString());
                        return jsonResponse;
                    }

                } catch (ClientException | SSLException e) {
                    // don't retry on client exception
                    throw e;
                } catch (HttpHostConnectException | ConnectTimeoutException e) {
                    throw new AnkaMgmtException(e);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new AnkaMgmtException(e);
                } finally {
                    httpClient.close();
                }
                return null;
            } catch (ClientException e) {
                // don't retry on client exception
                throw new AnkaMgmtException(e);
            } catch (Exception e) {
                if (retry >= maxRetries) {
                    continue;
                }
                throw new AnkaMgmtException(e);
            }
        }

    }

    protected CloseableHttpClient makeHttpClient() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException, CertificateException, IOException, UnrecoverableKeyException {
        RequestConfig.Builder requestBuilder = RequestConfig.custom();
        requestBuilder = requestBuilder.setConnectTimeout(timeout);
        requestBuilder = requestBuilder.setConnectionRequestTimeout(timeout);
        HttpClientBuilder builder = HttpClientBuilder.create();
        KeyStore keystore = null;
        if (rootCA != null) {
            PEMParser reader;
            BouncyCastleProvider bouncyCastleProvider = new BouncyCastleProvider();
            reader = new PEMParser(new StringReader(rootCA));
            X509CertificateHolder holder = (X509CertificateHolder)reader.readObject();
            Certificate certificate = new JcaX509CertificateConverter().setProvider(bouncyCastleProvider).getCertificate(holder);
            keystore = KeyStore.getInstance("JKS");
            keystore.load(null);
            keystore.setCertificateEntry("rootCA", certificate);
        }
        SSLContext sslContext = new SSLContextBuilder()
                    .loadTrustMaterial(keystore, getTrustStartegy()).build();
        builder.setSSLContext(sslContext);
        setTLSVerificationIfDefined(sslContext, builder);
        CloseableHttpClient httpClient = builder.setDefaultRequestConfig(requestBuilder.build()).build();
        return httpClient;

    }

    protected void setTLSVerificationIfDefined(SSLContext sslContext, HttpClientBuilder builder) {
        if (skipTLSVerification) {
            builder.setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier()));
        }
    }

    protected TrustStrategy getTrustStartegy() {
        if (skipTLSVerification) {
            return (certificate, authType) -> true;
        }
        return null;
    }

    protected HttpRequestBase setBody(HttpEntityEnclosingRequestBase request, JSONObject requestBody) throws UnsupportedEncodingException {
        request.setHeader("content-type", "application/json");
        StringEntity body = new StringEntity(requestBody.toString());
        request.setEntity(body);
        return request;
    }

    class HttpDeleteWithBody extends HttpEntityEnclosingRequestBase {
        public static final String METHOD_NAME = "DELETE";

        public String getMethod() {
            return METHOD_NAME;
        }

        public HttpDeleteWithBody(final String uri) {
            super();
            setURI(URI.create(uri));
        }

        public HttpDeleteWithBody(final URI uri) {
            super();
            setURI(uri);
        }

        public HttpDeleteWithBody() {
            super();
        }
    }


}
