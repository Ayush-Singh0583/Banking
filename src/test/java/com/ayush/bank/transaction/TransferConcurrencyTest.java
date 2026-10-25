package com.ayush.bank.transaction;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ayush.bank.account.Account;
import com.ayush.bank.account.AccountRepository;


@SpringBootTest
class TransferConcurrencyTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void twentyConcurrentTransfersCannotOverspend() throws Exception {
        // The test creates its OWN accounts, so it doesn't depend on data.sql
        Long fromId = accountRepository.save(new Account("Sender", new BigDecimal("1000.00"))).getId();
        Long toId   = accountRepository.save(new Account("Receiver", new BigDecimal("500.00"))).getId();

        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch startGun = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        for (int i = 0; i < 20; i++) {
            pool.submit(() -> {
                startGun.await();
                try {
                    transferService.transfer(fromId, toId, new BigDecimal("100"));
                    succeeded.incrementAndGet();
                } catch (InsufficientBalanceException e) {
                    rejected.incrementAndGet();
                } catch (Exception e) {
                    unexpected.incrementAndGet();      // anything else = a bug
                }
                return null;
            });
        }
        startGun.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(succeeded.get()).isEqualTo(10);
        assertThat(rejected.get()).isEqualTo(10);
        assertThat(unexpected.get()).isEqualTo(0);
        assertThat(accountRepository.findById(fromId).orElseThrow().getBalance()).isEqualByComparingTo("0.00");
        assertThat(accountRepository.findById(toId).orElseThrow().getBalance()).isEqualByComparingTo("1500.00");
    }
}
