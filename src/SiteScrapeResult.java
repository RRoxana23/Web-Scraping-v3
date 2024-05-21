import java.util.List;

class SiteScrapeResult {
    private final String site;
    private final List<Product> products;
    private final long duration;

    public SiteScrapeResult(String site, List<Product> products, long duration) {
        this.site = site;
        this.products = products;
        this.duration = duration;
    }

    public String getSite() {
        return site;
    }

    public List<Product> getProducts() {
        return products;
    }

    public int getProductCount() {
        return products.size();
    }

    public long getDuration() {
        return duration;
    }
}