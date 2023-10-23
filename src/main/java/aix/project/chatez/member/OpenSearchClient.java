package aix.project.chatez.member;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;

public class OpenSearchClient {
//    @Value("${open-search.hostname}")
//    private static String hostnameInstance;
//
//    @Value("${open-search.username}")
//    private static String usernameInstance;
//
//    @Value("${open-search.password}")
//    private static String passwordInstance;

    private static final String HOSTNAME;
    private static final int PORT = 443;
    private static final String SCHEME = "https";
    private static final String USERNAME;
    private static final String PASSWORD;

    public static RestHighLevelClient createClient() {

        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(USERNAME, PASSWORD));

        return new RestHighLevelClient(
                RestClient.builder(new HttpHost(HOSTNAME, PORT, SCHEME))
                        .setHttpClientConfigCallback(httpClientBuilder ->
                                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
        );
    }
}