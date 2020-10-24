package io.zeitmaschine.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "s3")
public class S3Config {

    private String host;
    private String bucket;
    private String cacheBucket;
    private boolean webhook;
    private Access access;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public void setCacheBucket(String cacheBucket) {
        this.cacheBucket = cacheBucket;
    }

    public String getCacheBucket() {
        return cacheBucket;
    }

    public String getBucket() {
        return bucket;
    }

    public Access getAccess() {
        return access;
    }

    public void setAccess(Access access) {
        this.access = access;
    }

    public boolean isWebhook() {
        return webhook;
    }

    public void setWebhook(boolean webhook) {
        this.webhook = webhook;
    }

    // needs to be static, PITA exception otherwise
    public static class Access {

        private String key;
        private String secret;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }
}
