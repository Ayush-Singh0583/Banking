# NOTES: Secure Banking Transaction API

## What I built (v1, 21 Oct 2026)
- `account/Account.java`: entity for the `account` table: `Long id`, `ownerName`, `BigDecimal balance` (precision 19, scale 2). Getters only, no setters.
- `account/AccountRepository.java`: Spring Data repository with `findByIdForUpdate(id)`, which uses `@Lock(PESSIMISTIC_WRITE)` and runs `SELECT ... FOR UPDATE`.
- One Spring Boot app with one H2 database, and packages grouped by feature (a modular monolith).

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

## Mistakes I made (21 Oct)
- I used `int id` → `Long` is better (`null` means not saved yet, and there's no 2.1 billion limit).
- I wrote `Protected final Account()` → Java keywords are lowercase, and a constructor can't be `final`. The fix is `protected Account() {}`.
- I wrote getters with no return type → every method needs one: `public Long getId() { return id; }`.
- I thought a pessimistic lock stops double-taps → that's the idempotency key's job. The lock only makes requests take turns.
- I thought a test had to extend some JPA class → it's just `new AccountService(fakeRepo)`.
- I didn't know why `TransferService` can't use `AccountRepository` directly → because of module boundaries: it has to go through `AccountService`.

## Next
- Module 3: `TransferService.transfer()` with `@Transactional`: lock the smaller id first, `InsufficientBalanceException extends RuntimeException`, debit and credit in one transaction, plus `AccountService.getForUpdate()` and `Account.debit()`/`credit()`
- Module 4: `POST /transfers` + request DTO + validation + `@RestControllerAdvice`, tested with curl (including the insufficient-balance case)
- `customer` module (replace `ownerName` with a real Customer)
- Idempotency key: a unique column so a double-tap doesn't charge twice
- Double-entry ledger table
- Dockerfile
- JWT / Spring Security
- Swagger
