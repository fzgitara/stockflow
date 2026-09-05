package com.stockflow.seed;

import com.stockflow.product.Product;
import com.stockflow.product.ProductRepository;
import com.stockflow.user.User;
import com.stockflow.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * N3: demo data so reviewers can click around within a minute.
 * Enabled with SEED_ENABLED=true; idempotent — skips if the demo user exists.
 * Credentials are documented in the README (demo-only account).
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    public static final String DEMO_EMAIL = "demo@stockflow.local";
    public static final String DEMO_PASSWORD = "password123";

    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public DataSeeder(UserRepository userRepository, ProductRepository productRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (userRepository.existsByEmailIgnoreCase(DEMO_EMAIL)) {
            log.info("Seed skipped: demo user already exists");
            return;
        }
        User demo = new User(DEMO_EMAIL, new BCryptPasswordEncoder(12).encode(DEMO_PASSWORD));
        demo = userRepository.save(demo);

        List<Product> products = List.of(
                new Product(demo.getId(), "SKU-001", "Arabica Coffee Beans 1kg", "Medium roast, whole bean", new BigDecimal("175000.00"), 20),
                new Product(demo.getId(), "SKU-002", "Robusta Coffee Beans 1kg", "Dark roast, whole bean", new BigDecimal("90000.00"), 15),
                new Product(demo.getId(), "SKU-003", "Green Tea Powder 250g", "Ceremonial grade matcha", new BigDecimal("120000.00"), 30),
                new Product(demo.getId(), "SKU-004", "Stainless Tumbler 500ml", "Double-walled, matte black", new BigDecimal("250000.00"), 12),
                new Product(demo.getId(), "SKU-005", "Canvas Tote Bag", "Natural cotton, screen printed", new BigDecimal("85000.00"), 40),
                new Product(demo.getId(), "SKU-006", "Glass Storage Jar 1L", "Airtight bamboo lid", new BigDecimal("65000.00"), 25),
                new Product(demo.getId(), "SKU-007", "Pour-Over Kettle 1L", "Gooseneck spout, stovetop", new BigDecimal("320000.00"), 8),
                new Product(demo.getId(), "SKU-008", "Paper Filter 100pcs", "V60 compatible, natural", new BigDecimal("45000.00"), 60));
        productRepository.saveAll(products);

        log.info("Seed complete: demo user {} with {} products", DEMO_EMAIL, products.size());
    }
}
