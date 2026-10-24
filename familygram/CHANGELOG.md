# FamilyGram 1.0.0

- FamilyGram workspace with search and existing family/privacy controls.
- Shared shopping/tasks and family calendar cards in conversations.
- Couple album with the native media calendar; shared Ours bookmarks.
- Couple widget and gallery-photo launcher shortcuts.
- Hugs with animation/haptics and random heart/kiss reactions.
- Daily mutual question with local answer commitment and reveal.
- Shared goals with manual contributions and itemized bill splitting.
- Explicitly joined synchronized playback of a video from the partner chat.
- Unheard voice queue, ExoPlayer silence skipping, saved playback position.
- Private screen, opt-in stable-release update checks and APK download button.
- Resumable tic-tac-toe and four-in-a-row.
- History-based conversation statistics and a shareable year summary.

Both participants need FamilyGram for interactive controls. Ordinary Telegram
clients retain readable transport messages. Gallery photos create additional
launcher shortcuts; changing the main app icon uses the images bundled in the
APK. Previous photo aliases still use the existing bundled photo until more
source photos are supplied. Statistics show whether full history was indexed.
One-time/disappearing media and secret chats are not indexed by this workspace.

## Улучшения общего пространства

- Убраны кнопки FamilyGram под обычными сообщениями; у объятий осталась кнопка ответа.
- Служебные операции скрыты из ленты и обычных уведомлений FamilyGram. Обмен через Telegram сохраняется; другие клиенты могут показывать служебный текст.
- Двойное нажатие в чате половинки выбирает доступную реакцию: красное сердце, огненное сердце или поцелуй.
- Добавлена защита от быстрых повторных отправок и повторного подтверждения формы.
- Настройки сгруппированы, добавлены значки, пояснения и переключатели; показаны требования к клиенту второго участника.
- В совместном просмотре добавлены явный выход и состояния участников. При обрыве связи состояние устаревает через 90 секунд.
- При синхронизации онлайн-приватности не отправляется неполный список исключений; готовые серверные правила переиспользуются при переподключении.

## FamilyGram 1.1.0

- Главная с плитками, любимыми разделами и быстрым доступом к настройкам.
- Персональный порядок разделов, скрытие и закрепление, восстановление исходного вида.
- Незаметный просмотр обычных и профильных историй; исключения для семьи и половинки.
- Отдельное скрытие остальных историй из ленты и блокировка их открытия.
- Публичные APK из GitHub Releases, автоматическая публикация после ручной сборки
  по умолчанию, распознавание повторных сборок по версии Android и тегу релиза.
- Проверена официальная база Telegram: 13.0.3 (7183); исходники 13.1.0 пока отсутствуют.

## FamilyGram 1.2.0

- Пять новых фото-иконок и авторская иконка «Сердце FamilyGram»; стандартные
  переключаемые компоненты сохранены, все варианты доступны без Premium.
- Превью иконок, анимации плиток и нажатий, сердечки для объятий, отключение эффектов.
- Python-плагины: локальный импорт, жизненный цикл, настройки, события сообщений,
  пример подписи с сердцем, отключение при ошибке и раздельное хранение аккаунтов.
- FamilyGram SDK 1: совместимость с полным SDK exteraGram/Xposed не заявляется.
- Минимальная версия Android повышена до 7.0 для встраиваемого Python.
