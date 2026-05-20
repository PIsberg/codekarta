module com.karta.shipping {
    requires java.base;
    requires java.logging;
    exports com.karta.shipping.domain;
    exports com.karta.shipping.core;
    exports com.karta.shipping.state;
}
