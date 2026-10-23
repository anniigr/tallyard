package io.tallyard.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.tallyard.billing.PeriodState;
import io.tallyard.shared.id.Ids;
import io.tallyard.shared.money.Money;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SharedUnitTests {

    @Nested
    class MoneyTest {
        final Currency usd = Currency.getInstance("USD");

        @Test
        void arithmeticIsExactAndScaleIsNormalised() {
            Money a = Money.of("0.1", "USD");
            Money b = Money.of("0.2", "USD");
            assertThat(a.plus(b).amount()).isEqualByComparingTo("0.3");
            assertThat(a.plus(b).amount().scale()).isEqualTo(Money.SCALE);
            assertThat(Money.of("10", "USD").times(new BigDecimal("0.000002")).amount())
                    .isEqualByComparingTo("0.000020");
        }

        @Test
        void roundingIsBankers() {
            assertThat(Money.of("2.225", "USD").rounded().amount()).isEqualByComparingTo("2.22");
            assertThat(Money.of("2.235", "USD").rounded().amount()).isEqualByComparingTo("2.24");
            assertThat(Money.of("2.2251", "USD").rounded().amount()).isEqualByComparingTo("2.23");
            assertThat(Money.of("100.4", "JPY").rounded().amount()).isEqualByComparingTo("100");
        }

        @Test
        void currenciesCannotBeMixed() {
            assertThatThrownBy(() -> Money.of("1", "USD").plus(Money.of("1", "EUR")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void comparisons() {
            assertThat(Money.of("1", "USD").min(Money.of("2", "USD"))).isEqualTo(Money.of("1", "USD"));
            assertThat(Money.zero(usd).isZero()).isTrue();
            assertThat(Money.of("-1", "USD").isNegative()).isTrue();
        }
    }

    @Nested
    class IdsTest {
        @Test
        void uuidV7IsTimeOrderedAndUnique() {
            Set<UUID> seen = new HashSet<>();
            UUID previous = Ids.newId(1_000L);
            for (long t = 1_001L; t < 6_000L; t++) {
                UUID id = Ids.newId(t);
                assertThat(id.version()).isEqualTo(7);
                assertThat(id.variant()).isEqualTo(2);
                assertThat(id.toString().compareTo(previous.toString())).isGreaterThan(0);
                assertThat(Ids.timestampMillis(id)).isEqualTo(t);
                assertThat(seen.add(id)).isTrue();
                previous = id;
            }
        }
    }

    @Nested
    class PeriodStateTest {
        @Test
        void transitionsAreLinearAndIrreversible() {
            assertThat(PeriodState.OPEN.canTransitionTo(PeriodState.CLOSING)).isTrue();
            assertThat(PeriodState.CLOSING.canTransitionTo(PeriodState.RATED)).isTrue();
            assertThat(PeriodState.RATED.canTransitionTo(PeriodState.FINALIZED)).isTrue();
            assertThat(PeriodState.OPEN.canTransitionTo(PeriodState.RATED)).isFalse();
            assertThat(PeriodState.RATED.canTransitionTo(PeriodState.OPEN)).isFalse();
            assertThat(PeriodState.FINALIZED.canTransitionTo(PeriodState.FINALIZED))
                    .isFalse();
            assertThat(PeriodState.OPEN.acceptsUsage()).isTrue();
            assertThat(PeriodState.CLOSING.acceptsUsage()).isTrue();
            assertThat(PeriodState.RATED.acceptsUsage()).isFalse();
        }
    }
}
