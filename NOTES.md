# NOTES: Secure Banking Transaction API

## What I built (v1, 21 Oct 2026)
- `account/Account.java`: entity for the `account` table: `Long id`, `ownerName`, `BigDecimal balance` (precision 19, scale 2). Getters only, no setters.
- `account/AccountRepository.java`: Spring Data repository with `findByIdForUpdate(id)`, which uses `@Lock(PESSIMISTIC_WRITE)` and runs `SELECT ... FOR UPDATE`.
- One Spring Boot app with one H2 database, and packages grouped by feature (a modular monolith).
- (22 Oct) `account/AccountService.java`: the account module's public door. `getForUpdate(id)` returns a locked account.
- (22 Oct) `transaction/TransferService.java`: `@Transactional transfer(fromId, toId, amount)`. Locks the smaller id first, checks the balance, then debits and credits.
- (22 Oct) `transaction/InsufficientBalanceException.java`: extends `RuntimeException` so Spring rolls back.
- (22 Oct) `transaction/TransferRequest.java`: record DTO. `@NotNull` ids, `@NotNull @Positive` amount.
- (22 Oct) `transaction/TransferController.java`: `POST /transfers` with `@Valid @RequestBody`.
- (22 Oct) `transaction/GlobalExceptionHandler.java`: `@RestControllerAdvice` turns `InsufficientBalanceException` into 409.
- (22 Oct) `data.sql` creates 2 test accounts (Ayush 1000, Riya 500), and the H2 console is at `/h2-console`.
- **Tested end to end with curl:** ₹250 → 200 (balances 750 / 750), ₹5000 → 409, −₹500 → 400. The failed requests changed nothing.

## What I learned (in my own words)

### Layers: Controller → Service → Repository → Entity
The Controller only handles the HTTP calls: it reads the request and sends the response back. The Service holds the business rules (like "is there enough balance?"), and it's where transactions start. The Repository does the CRUD with the database, and Spring Data writes the SQL. An Entity is a Java class mapped to a table, where one object is one row.
We split them so each layer has one job and can be changed or tested on its own.

### Account entity design
- The empty constructor is `protected`: JPA needs it to build objects from rows, and `protected` stops anyone from using it by accident.
- No `setBalance()`: otherwise anyone could set my balance to 1000 billion and I'd be richer than Elon Musk. The balance should only change through methods that check the rules first.
- `Long id`, not `int`: `null` clearly means "not saved yet" (0 looks like a real id), and `int` runs out at about 2.1 billion.

### Constructor injection
It removes the need for Spring during a test: I can create the class myself with `new` and pass in a mock or fake, like `new AccountService(fakeRepo)`. With `@Autowired` on a private field, nothing fills the field when Spring isn't running, so it stays `null` and throws a NullPointerException. Bonus: the field can be `final`.

### BigDecimal, not double
`double` stores numbers in binary, so it can't store values like 0.1 exactly. On the surface it looks like 13.45, but it might really be 13.4500008, and that error adds up over many transactions. BigDecimal stores exact decimal digits.
Trap: `equals` also checks the scale, so 12.00 is not equal to 12.0. Compare with `compareTo(...) == 0`. Also create a BigDecimal from a String, `new BigDecimal("0.1")`, not `new BigDecimal(0.1)`.

### Pessimistic lock (PESSIMISTIC_WRITE)
It stops another transaction from locking or changing the same row until the transaction holding the lock is finished. The other one waits. The lock lasts until the first transaction ends (commit or rollback), so it only works inside `@Transactional`.

### Lock vs idempotency key
- Lock: two *different* requests on the same row take turns, so no update is lost. With ₹100 and two ₹80 transfers, only one succeeds.
- Idempotency key: stops a *duplicate* transaction (a double-tap). The lock alone doesn't stop it: with ₹500, both "Pay ₹50" taps go through.

### Modular monolith
The code is grouped by feature (account and transaction now, customer and auth later), but it's still one app with one database, so a transfer can run inside one transaction. A module only calls another module's Service, never its Repository. It's like asking the teller instead of walking into the vault: the account rules live in one place, and no other module can skip them.

### @Transactional (Module 3)
The code runs line by line, but the database only keeps the changes if the whole method finishes. If a RuntimeException escapes, everything is rolled back. Example: if the power goes out right after `debit()`, the balance is still ₹100 because nothing was committed. A checked exception still commits by default, which is why `InsufficientBalanceException` extends `RuntimeException`.
`@Transactional` works through a proxy. If a method calls `transfer()` from the same class, it skips the proxy, so there's no transaction. Fix: put `@Transactional` on the outer method, or call `transfer()` from another bean.
Deadlock: always lock the smaller id first, so two transfers never end up waiting on each other in a circle.
Inside one module, the service uses its own repository. Only other modules have to go through the service.

### Controller, DTO, validation and errors (Module 4)
The controller only takes the request and calls the service. The request comes in as a DTO (`TransferRequest`), never as the `Account` entity, so a client can't send their own balance.
`@Valid` makes Spring check `@NotNull` and `@Positive` before my code runs, and bad input gets a 400. Without `@Valid`, a hacker could skip the app and send −500 with curl: the balance check passes, Ayush goes up to 1250 and Riya goes down to 250. Never trust the client.
`@RestControllerAdvice` catches my exceptions from every controller and turns them into clean responses: not enough money → 409 with my message.
An account that doesn't exist should give 404 Not Found. Right now it gives 500, because nothing handles that exception yet.

## Mistakes I made (21 Oct)
- I used `int id` → `Long` is better (`null` means not saved yet, and there's no 2.1 billion limit).
- I wrote `Protected final Account()` → Java keywords are lowercase, and a constructor can't be `final`. The fix is `protected Account() {}`.
- I wrote getters with no return type → every method needs one: `public Long getId() { return id; }`.
- I thought a pessimistic lock stops double-taps → that's the idempotency key's job. The lock only makes requests take turns.
- I thought a test had to extend some JPA class → it's just `new AccountService(fakeRepo)`.
- I didn't know why `TransferService` can't use `AccountRepository` directly → because of module boundaries: it has to go through `AccountService`.

- (22 Oct) I thought a service exists so we don't use the repository → the service *does* use its own repository; only *other* modules must go through the service.
- (22 Oct) I didn't know `Math.min` / `Math.max` exist → for library methods, look them up.
- (22 Oct) I used `@Controller` → for an API it's `@RestController` (`@Controller` is for HTML pages).
- (22 Oct) I used `Account`'s getters on the request → the request is a `TransferRequest`, so it's `request.fromId()`, `toId()`, `amount()`.
- (22 Oct) VS Code inserted a snippet method when I typed `@PostMapping` → press Esc instead of Enter on the suggestion list.
- (22 Oct) I thought invalid input reaches my code as a runtime exception → with `@Valid`, Spring answers 400 before my code runs.
- (22 Oct) I thought a missing account returns 400 → it returns 500 right now (nothing handles it). It should be 404.

## Next (before the interview on 29 Oct, most interview value first)
1. **Fix the bugs from the code review:**
   - **Paisa bug:** `scale = 2` rounds silently, and `@Positive` allows ₹0.005. First check it with `curl ... "amount":0.005` and look in the H2 console. Fix: `@Digits(integer = 17, fraction = 2)` on `amount`.
   - **Same-account transfer:** `transfer(1, 1, 100)` returns 200. Reject `fromId == toId`.
   - **`debit()` / `credit()` check nothing:** make them reject amounts ≤ 0, so no caller can skip the check.
   - `AccountNotFoundException` (extends `RuntimeException`) + a handler → 404 instead of 500.
   - Move `GlobalExceptionHandler` from `transaction/` to a shared `common/` package, because it handles errors for every module.
2. **Prove the lock works:** fire 20 × ₹100 transfers at once against ₹1000. Expect exactly ten 200s and ten 409s, with Ayush at 0.00 and Riya at 1500.00. Then swap `findByIdForUpdate` for `findById`, run it again, and watch money appear. Then turn it into a JUnit test.
3. Idempotency key: a unique column so a double-tap doesn't charge twice.
4. PostgreSQL in Docker Compose, plus a Dockerfile.
5. Double-entry ledger table, then Swagger.
6. JWT / Spring Security, last because it's the hardest. **If it isn't working by 28 Oct, remove it from the resume.**
- Later: `customer` module (replace `ownerName`), and a unit test for `TransferService` with a fake `AccountService`.
