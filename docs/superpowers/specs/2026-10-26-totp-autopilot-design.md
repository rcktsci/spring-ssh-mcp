# TOTP-автопилот (SSH OTP autopilot) — дизайн

Дата: 2026-10-26. Статус: утверждено (вариант A, «полный автопилот»).

## Зачем

Серверы с двухфакторной аутентификацией (пароль + TOTP / OTP через
keyboard-interactive) сейчас не подключаются вообще: sshj падает с
«Exhausted available authentication methods». Нужно:

1. Реально подключаться: авто-генерация кода TOTP из секрета и ответ на
   OTP-промпт сервера.
2. Понятно сообщать, что сервер просит OTP (вместо криптической ошибки).

## Подход (выбран вариант A)

Новая колонка `servers.totp_secret` (TEXT, шифруется как пароль). Если
секрет задан — при подключении пробуем `keyboard-interactive`
(`AuthKeyboardInteractive` + свой `ChallengeResponseProvider`): на промпт
«пароль» отвечаем сохранённым паролем, на OTP-промпт — кодом TOTP по
RFC 6238 (base32-декодирование секрета, HmacSHA1, счётчик unix/30 сек,
6 цифр). Если keyboard-interactive недоступен — откат на текущий
пароль/ключ (поведение без секрета не меняется).

Если секрета нет, а сервер запросил OTP — понятная ошибка
«сервер требует одноразовый код (OTP), задайте TOTP-секрет» вместо
«Exhausted available authentication methods».

## Объём

- Миграция `2026-10-26__add_column__servers__totp_secret.xml` (TEXT,
  nullable, preCondition MARK_RAN).
- `ServerEntity.totpSecret` — шифрованное поле (как password).
- `TotpGenerator` — RFC 6238 (base32 decode, HmacSHA1, dynamic truncation).
- `OtpChallengeProvider` — ChallengeResponseProvider sshj: классификатор
  промптов (пароль / OTP / неизвестно), домен «OTP» и «пароль».
- `SshService.authenticate` — путь с OTP, откат, понятные ошибки.
- `ServerManagementService.addServer/updateServer` — приём + валидация
  base32, шифрование.
- `AddServerTool` (MCP) + `ServerController` (REST) + `index.html`
  (поле в форме со подсказкой, откуда взять секрет).
- Тесты: юнит-вектора RFC 6238 + юнит классификатора; интеграционный
  e2e с реальным embedded SSH-сервером (Apache MINA SSHD), который
  запрашивает «Verification code:» по keyboard-interactive.

## Параметры TOTP

- Алгоритм: HMAC-SHA1 (как Google Authenticator / FreeOTP).
- Период: 30 сек. Длина кода: 6 цифр. Реализация принимает digits для
  тестов по RFC (8 цифр).
- Секрет: base32 (RFC 4648), пробелы/`=` игнорируются; валидация при
  сохранении сервера.

## Тесты (внешняя проверка корректности)

- RFC 6238 Appendix B (SHA-1) — точные ожидаемые коды при известных
  T: 59 → 94287082; 1111111109 → 07081804; 1111111111 → 14050471;
  1234567890 → 89005924; 2000000000 → 69279037; 20000000000 → 65353130.
- Интеграция: MINA SSHD спрашивает «Verification code:» и проверяет
  TOTP от известного секрета; MCP-инструмент `execute` проходит
  успешно; неправильный секрет → понятная ошибка; отсутствие секрета
  при OTP-сервере → ошибка с подсказкой.

## Замечания

- Изменение поведения только при заданном секрете; существующие серверы
  без OTP работают как раньше (откат на authPassword/authPublickey).
- Секрет не возвращается API (как прочие секреты), в UI поле пустое
  при редактировании = «не менять».
