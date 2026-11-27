package com.example.money_saver_android.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class I18nManagerTest {

    @Test
    fun test_nested_keys_resolution() {
        val i18n = I18nManager()
        val json = """
            {
              "expenses": {
                "header": {
                  "title": "Gastos"
                }
              }
            }
        """.trimIndent()
        i18n.loadTranslation("es", json)
        i18n.setLanguage("es")
        assertEquals("Gastos", i18n.t("expenses.header.title"))
    }

    @Test
    fun test_language_manager_loads_and_translates_keys_for_all_locales() {
        val i18n = I18nManager()

        val esJson = """{"navigation": {"tab_expenses": "Gastos", "tab_settings": "Configuración"}}"""
        val enJson = """{"navigation": {"tab_expenses": "Expenses", "tab_settings": "Settings"}}"""
        val deJson = """{"navigation": {"tab_expenses": "Ausgaben", "tab_settings": "Einstellungen"}}"""
        val ptJson = """{"navigation": {"tab_expenses": "Despesas", "tab_settings": "Configurações"}}"""

        i18n.loadTranslation("es", esJson)
        i18n.loadTranslation("en", enJson)
        i18n.loadTranslation("de", deJson)
        i18n.loadTranslation("pt", ptJson)

        i18n.setLanguage("es")
        assertEquals("Gastos", i18n.t("navigation.tab_expenses"))
        assertEquals("Configuración", i18n.t("navigation.tab_settings"))

        i18n.setLanguage("en")
        assertEquals("Expenses", i18n.t("navigation.tab_expenses"))
        assertEquals("Settings", i18n.t("navigation.tab_settings"))

        i18n.setLanguage("de")
        assertEquals("Ausgaben", i18n.t("navigation.tab_expenses"))
        assertEquals("Einstellungen", i18n.t("navigation.tab_settings"))

        i18n.setLanguage("pt")
        assertEquals("Despesas", i18n.t("navigation.tab_expenses"))
        assertEquals("Configurações", i18n.t("navigation.tab_settings"))
    }

    @Test
    fun test_app_lock_translations_present_in_all_four_languages() {
        val i18n = I18nManager()
        i18n.initFromContext(RuntimeEnvironment.getApplication())

        val requiredKeys = listOf(
            "settings.security_section.title",
            "settings.security_section.description",
            "settings.security_section.toggle_label",
            "settings.security_section.capability_biometric_and_pin",
            "settings.security_section.capability_pin_only",
            "settings.security_section.capability_none",
            "app_lock.prompt_title",
            "app_lock.prompt_subtitle",
            "app_lock.screen_title",
            "app_lock.screen_subtitle",
            "app_lock.auth_error",
            "app_lock.retry_button",
            "loans.card.due_prefix",
            "loans.schedule.section_title",
            "loans.schedule.date_label",
            "loans.schedule.amount_label",
            "loans.schedule.add_button",
            "loans.schedule.empty_message",
            "loans.schedule.sum_label",
            "loans.schedule.dialog_title",
            "loans.schedule.save_button",
            "loans.schedule.remove_content_desc",
            "loans.schedule.overdue_label",
            "loans.schedule.cancel_button",
            "loans.options.manage_installments",
            "loans.options.edit",
            "loans.options.delete",
            "loans.edit_dialog.title",
            "loans.edit_dialog.save_button",
            "loans.delete_dialog.title",
            "loans.delete_dialog.warning_message",
            "loans.delete_dialog.cancel_button",
            "loans.messages.updated",
            "loans.messages.deleted",
            "loans.messages.schedule_updated",
            "loans.messages.payment_saved",
            "loans.actions.register_payment",
            "loans.card.settled_badge",
            "loans.payment_dialog.title",
            "loans.payment_dialog.amount_label",
            "loans.payment_dialog.pay_full_button",
            "loans.payment_dialog.datetime_label",
            "loans.payment_dialog.saving_destination_label",
            "loans.payment_dialog.saving_destination_placeholder",
            "loans.payment_dialog.auto_credited_tag",
            "loans.payment_dialog.will_settle_hint",
            "loans.payment_dialog.remaining_balance_hint",
            "loans.payment_dialog.new_due_date_label",
            "loans.payment_dialog.partial_schedule_title",
            "loans.payment_dialog.schedule_keep",
            "loans.payment_dialog.schedule_add",
            "loans.payment_dialog.schedule_adjust",
            "loans.payment_dialog.next_installment_label",
            "loans.payment_dialog.remainder_due_date_label",
            "loans.payment_dialog.save_button",
            "loans.add_dialog.payment_plan_label",
            "loans.add_dialog.payment_mode_none",
            "loans.add_dialog.payment_mode_single",
            "loans.add_dialog.payment_mode_installments",
            "loans.add_dialog.single_due_date_label",
            "loans.schedule.options_content_desc",
            "loans.schedule.edit_installment",
            "loans.schedule.update_installment_button",
            "loans.schedule.discard_dialog_title",
            "loans.schedule.discard_dialog_message",
            "loans.schedule.discard_cancel",
            "loans.schedule.discard_confirm",
            "loans.schedule.paid_label",
            "loans.payment_dialog.target_installment_label",
            "loans.payment_dialog.single_scheduled_prefix",
            "loans.payment_dialog.installment_number_prefix",
            "loans.payment_dialog.installment_detail",
            "loans.payment_dialog.overflow_installment_hint",
            "loans.payment_dialog.optional_new_due_date_toggle",
            "loans.edit_dialog.discard_dialog_title",
            "loans.edit_dialog.discard_dialog_message",
            "loans.options.payment_history",
            "loans.history_sheet.paid_percent_label",
            "loans.history_sheet.installments_progress",
            "loans.history_sheet.payments_section_title",
            "loans.history_sheet.payment_singular",
            "loans.history_sheet.payment_plural",
            "loans.history_sheet.empty_message",
            "loans.history_sheet.register_new_payment",
            "loans.history_sheet.installment_of_total",
            "loans.history_sheet.debt_payment_tag",
            "loans.history_sheet.full_payment_badge",
            "loans.history_sheet.partial_payment_badge",
            "loans.history_sheet.edit_payment",
            "loans.history_sheet.delete_payment",
            "loans.payment_dialog.edit_title",
            "loans.messages.payment_updated",
            "loans.messages.payment_deleted",
            "common.delete_record_confirmation",
            "common.delete_action",
            "common.cancel_action"
        )

        listOf("es", "en", "de", "pt").forEach { locale ->
            i18n.setLanguage(locale)
            requiredKeys.forEach { key ->
                val resolved = i18n.t(key)
                assertNotEquals("Missing key $key for locale $locale", key, resolved)
                assertTrue("Blank key $key for locale $locale", resolved.isNotBlank())
            }
        }
    }
}
