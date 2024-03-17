package ir.ramtung.tinyme.domain;

import ir.ramtung.tinyme.domain.entity.*;
import ir.ramtung.tinyme.domain.service.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
public class BrokerCreditTest {
    private Security security;
    private Shareholder shareholder;
    private Broker initialBroker1;
    private Broker initialBroker2;
    private OrderBook orderBook;
    private List<Order> orders;
    @Autowired
    private Matcher matcher;

    @BeforeEach
    void setupOrderBook() {
        security = Security.builder().build();
        initialBroker1 = Broker.builder().credit(100_000_000L).build();
        initialBroker2 = Broker.builder().credit(100_000_000L).build();
        shareholder = Shareholder.builder().build();
        shareholder.incPosition(security, 100_000);
        orderBook = security.getOrderBook();
        orders = Arrays.asList(
                new Order(1, security, Side.BUY, 304, 15700, initialBroker1, shareholder),
                new Order(2, security, Side.BUY, 43, 15500, initialBroker2, shareholder),
                new Order(3, security, Side.BUY, 445, 15450, initialBroker1, shareholder),
                new Order(4, security, Side.BUY, 526, 15450, initialBroker2, shareholder),
                new Order(5, security, Side.BUY, 1000, 15400, initialBroker1, shareholder),
                new Order(6, security, Side.SELL, 350, 15800, initialBroker2, shareholder),
                new Order(7, security, Side.SELL, 285, 15810, initialBroker1, shareholder),
                new Order(8, security, Side.SELL, 800, 15810, initialBroker2, shareholder),
                new Order(9, security, Side.SELL, 340, 15820, initialBroker1, shareholder),
                new Order(10, security, Side.SELL, 65, 15820, initialBroker2, shareholder)
        );
        orders.forEach(order -> orderBook.enqueue(order));
    }

    @Test
    void broker_has_enough_credit_for_new_buy_order() {
        Broker broker = Broker.builder().credit(100_000_000L).build();
        Order order = new Order(11, security, Side.BUY, 10, 15800, broker, shareholder);
        Trade trade = new Trade(security, 15800, 10, order, orders.get(5));
        MatchResult result = matcher.match(order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(broker.getCredit()).isEqualTo(99_842_000L);
        assertThat(initialBroker2.getCredit()).isEqualTo(100_158_000L );
        assertThat(result.trades()).containsExactly(trade);
    }

    @Test
    void broker_does_not_have_enough_credit_for_new_buy_order() {
        Broker broker = Broker.builder().credit(100_000L).build();
        Order order = new Order(11, security, Side.BUY, 10, 15_800, broker, shareholder);
        MatchResult result = matcher.match(order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.NOT_ENOUGH_CREDIT);
        assertThat(broker.getCredit()).isEqualTo(100_000L);
        assertThat(initialBroker2.getCredit()).isEqualTo(100_000_000L );
        assertThat(result.trades()).containsExactly();
    }

    @Test
    void buy_order_matches_order_with_less_price() {
        Broker broker = Broker.builder().credit(100_000_000L).build();
        Order order = new Order(11, security, Side.BUY, 100, 16_000, broker, shareholder);
        Trade trade = new Trade(security, 15800, 100, order, orders.get(5));
        MatchResult result = matcher.match(order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(result.remainder().getQuantity()).isEqualTo(0);
        assertThat(broker.getCredit()).isEqualTo(98_420_000L);
        assertThat(initialBroker2.getCredit()).isEqualTo(101_580_000L );
        assertThat(result.trades()).containsExactly(trade);
    }

    @Test
    void buy_order_matches_many_orders_without_remainder() {
        Broker broker = Broker.builder().credit(100_000_000L).build();
        Order order = new Order(11, security, Side.BUY, 1000, 16_000, broker, shareholder);
        Order order1 = order.snapshotWithQuantity(650);
        Order order2 = order.snapshotWithQuantity(365);
        List<Trade> trades = Arrays.asList(
                new Trade(security, 15_800, 350, order, orders.get(5)),
                new Trade(security, 15_810, 285, order1, orders.get(6)),
                new Trade(security, 15_810, 365, order2, orders.get(7))
        );
        MatchResult result = matcher.match(order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(result.remainder().getQuantity()).isEqualTo(0);
        assertThat(broker.getCredit()).isEqualTo(84_193_500L);
        assertThat(initialBroker1.getCredit()).isEqualTo(104_505_850L);
        assertThat(initialBroker2.getCredit()).isEqualTo(111_300_650L);
        assertThat(result.trades()).containsExactlyElementsOf(trades);
    }
    @Test
    void buy_order_matches_with_remainder() {
        Broker broker = Broker.builder().credit(100_000_000L).build();
        Order order = new Order(11, security, Side.BUY, 1000, 15_800, broker, shareholder);
        Trade trade = new Trade(security, 15_800, 350, order, orders.get(5));
        MatchResult result = matcher.match(order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(result.remainder().getQuantity()).isEqualTo(650);
        assertThat(result.remainder().getPrice()).isEqualTo(15_800);
        assertThat(broker.getCredit()).isEqualTo(94_470_000L);
        assertThat(initialBroker2.getCredit()).isEqualTo(105_530_000L);
        assertThat(result.trades()).containsExactly(trade);
    }
    @Test
    void buy_order_matches_order_with_less_price_with_precise_credit() {
        Broker broker = Broker.builder().credit(1_580_000).build();
        Order order = new Order(11, security, Side.BUY, 100, 16_000, broker, shareholder);
        Trade trade = new Trade(security, 15800, 100, order, orders.get(5));
        MatchResult result = matcher.match(order);
        assertThat(result.outcome()).isEqualTo(MatchingOutcome.EXECUTED);
        assertThat(result.remainder().getQuantity()).isEqualTo(0);
        assertThat(broker.getCredit()).isEqualTo(0L);
        assertThat(initialBroker2.getCredit()).isEqualTo(101_580_000L);
        assertThat(result.trades()).containsExactly(trade);
    }
}
