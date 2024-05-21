import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Main {
    private static ExecutorService executor = Executors.newFixedThreadPool(10);

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

        List<Product> allProducts = new ArrayList<>();
        try {
            List<String> categories = List.of(
                    "dresses", "tops", "shoes", "jeans"
            );

            for (String category : categories) {
                String baseUrl = "https://www2.hm.com/en_gb/ladies/shop-by-product/" + category + ".html";
                CompletableFuture<SiteScrapeResult> future = CompletableFuture.supplyAsync(() -> scrapeSite(baseUrl), executor);
                SiteScrapeResult result = future.get();

                System.out.printf("Category: %s, Products extracted: %d, Time: %d ms%n",
                        category, result.getProductCount(), result.getDuration());

                allProducts.addAll(result.getProducts());

                System.out.println("Statistical analysis for category " + category + ":");
                double averagePrice = result.getProducts().stream()
                        .mapToDouble(Product::getPrice)
                        .average()
                        .orElse(0.0);
                System.out.println("Average price: " + averagePrice);

                double minPrice = result.getProducts().stream()
                        .mapToDouble(Product::getPrice)
                        .min()
                        .orElse(0.0);
                System.out.println("Minimum price: " + minPrice);

                double maxPrice = result.getProducts().stream()
                        .mapToDouble(Product::getPrice)
                        .max()
                        .orElse(0.0);
                System.out.println("Maximum price: " + maxPrice);
                System.out.println();
            }

        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        executor.shutdown();

        List<Product> topFiveProducts = getTopNMostExpensiveProducts(allProducts, 5);
        try {
            generateAndSaveChart(topFiveProducts);
        } catch (IOException e) {
            e.printStackTrace();
        }

        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;
        System.out.println("\nTotal execution time: " + executionTime + " milliseconds");
    }

    private static List<Product> getTopNMostExpensiveProducts(List<Product> products, int n) {
        return products.stream()
                .sorted(Comparator.comparingDouble(Product::getPrice).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    private static SiteScrapeResult scrapeSite(String baseUrl) {
        long startTime = System.currentTimeMillis();
        List<Product> products = new ArrayList<>();
        try {
            Document firstPage = Jsoup.connect(baseUrl).get();
            int totalPages = getTotalPages(firstPage);

            List<CompletableFuture<List<Product>>> pageFutures = new ArrayList<>();
            for (int page = 0; page < totalPages; page++) {
                final int currentPage = page;
                pageFutures.add(CompletableFuture.supplyAsync(() -> scrapePage(baseUrl, currentPage), executor));
            }

            for (CompletableFuture<List<Product>> future : pageFutures) {
                products.addAll(future.join());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        long endTime = System.currentTimeMillis();
        return new SiteScrapeResult(baseUrl, products, endTime - startTime);
    }

    private static List<Product> scrapePage(String baseUrl, int page) {
        List<Product> products = new ArrayList<>();
        String url = baseUrl + "?page=" + (page + 1);
        try {
            Document currentPage = Jsoup.connect(url).get();
            Elements productLinks = currentPage.select("div#products-listing-section li article");

            for (Element link : productLinks) {
                String name = link.select("h2, h3, h4").text();
                String price = link.select("p, span.price").text();
                products.add(new Product(name, parsePrice(price)));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return products;
    }

    private static int getTotalPages(Document firstPage) {
        Element paginationNav = firstPage.select("nav[aria-label=Pagination], ul.pagination").first();
        if (paginationNav != null) {
            Element lastPageElement = paginationNav.select("a[aria-label^=Go to page], a.page-link").last();
            if (lastPageElement != null) {
                String lastPageLink = lastPageElement.attr("href");
                String[] parts = lastPageLink.split("page=");
                if (parts.length >= 2) {
                    try {
                        return Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return 1;
    }

    private static double parsePrice(String priceText) {
        String cleanPriceText = priceText.replaceAll("[^\\d.]", "");
        int dotCount = 0;
        StringBuilder result = new StringBuilder();
        for (char c : cleanPriceText.toCharArray()) {
            if (c == '.' && dotCount == 0) {
                result.append(c);
                dotCount++;
            } else if (Character.isDigit(c)) {
                result.append(c);
            }
        }
        try {
            return Double.parseDouble(result.toString());
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return 0.0;
        }
    }

    private static void generateAndSaveChart(List<Product> products) throws IOException {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (Product product : products) {
            dataset.addValue(product.getPrice(), product.getName(), "Category");
        }

        JFreeChart barChart = ChartFactory.createBarChart(
                "Top 5 Most Expensive Products",
                "Product Name",
                "Price",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false);

        int width = 800;
        int height = 600;
        ChartUtils.saveChartAsPNG(new java.io.File("top_5_expensive_products_chart.png"), barChart, width, height);
    }
}


