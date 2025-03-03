package net.urosk.taskomir.core.ui;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.i18n.I18NProvider;

import java.util.Locale;

/**
 * Custom Vaadin field za sestavljanje in prikaz šestdelnega cron izraza:
 * (sekunda, minuta, ura, dan v mesecu, mesec, dan v tednu).
 * Prevodi za vse oznake se pridobivajo preko Vaadin I18NProvider.
 */
public class CronField extends CustomField<String> {

    private final ComboBox<String> secondBox;
    private final ComboBox<String> minuteBox;
    private final ComboBox<String> hourBox;
    private final ComboBox<String> dayOfMonthBox;
    private final ComboBox<String> monthBox;
    private final ComboBox<String> dayOfWeekBox;
    private final Dialog cronDialog = new Dialog();
    private final TextField textField = new TextField();

    private I18NProvider i18nProvider;
    private Locale currentLocale;

    public CronField() {
        super();



        // Inicializiraj ComboBoxe z prevodi
        secondBox = new ComboBox<>(getTranslation("cron.field.second", "Sekunda"));
        minuteBox = new ComboBox<>(getTranslation("cron.field.minute", "Minuta"));
        hourBox = new ComboBox<>(getTranslation("cron.field.hour", "Ura"));
        dayOfMonthBox = new ComboBox<>(getTranslation("cron.field.dayOfMonth", "Dan v mesecu"));
        monthBox = new ComboBox<>(getTranslation("cron.field.month", "Mesec"));
        dayOfWeekBox = new ComboBox<>(getTranslation("cron.field.dayOfWeek", "Dan v tednu"));

        textField.setReadOnly(true);
        Button cronButton = new Button(VaadinIcon.CALENDAR.create());
        textField.setSuffixComponent(cronButton);
        cronButton.addClickListener(this::openCronDialog);
        add(textField);
        textField.setHelperText(getTranslation("cron.field.helper", "Cron izraz"));

        initComboBoxes();
        initLayout();
    }



    private void openCronDialog(ClickEvent<Button> event) {
        cronDialog.open();
    }

    /**
     * Inicializira ComboBoxe z nekaterimi pogostimi možnostmi.
     */
    private void initComboBoxes() {
        secondBox.setItems("0", "*/5", "*/10", "*/15", "*");
        secondBox.setValue("0");

        minuteBox.setItems("0", "0/5", "0/10", "0/15", "0/30", "*");
        minuteBox.setValue("0/10");

        hourBox.setItems("*", "0", "12", "0-5", "8-16");
        hourBox.setValue("*");

        dayOfMonthBox.setItems("*", "1", "15", "20", "L", "?");
        dayOfMonthBox.setValue("*");

        monthBox.setItems("*", "1", "2", "3", "6", "12");
        monthBox.setValue("*");

        dayOfWeekBox.setItems("?", "*", "MON-FRI", "SUN", "0-4");
        dayOfWeekBox.setValue("?");

        // Posodobi vrednost modela ob spremembi vsakega ComboBoxa
        secondBox.addValueChangeListener(e -> updateValue());
        minuteBox.addValueChangeListener(e -> updateValue());
        hourBox.addValueChangeListener(e -> updateValue());
        dayOfMonthBox.addValueChangeListener(e -> updateValue());
        monthBox.addValueChangeListener(e -> updateValue());
        dayOfWeekBox.addValueChangeListener(e -> updateValue());
    }

    private void initLayout() {
        HorizontalLayout row1 = new HorizontalLayout(secondBox, minuteBox, hourBox);
        HorizontalLayout row2 = new HorizontalLayout(dayOfMonthBox, monthBox, dayOfWeekBox);
        VerticalLayout layout = new VerticalLayout(row1, row2);
        Button saveButton = new Button(getTranslation("cron.field.saveButton", "Uporabi"), e -> {
            setValue(buildCronExpression());
            textField.setValue(buildCronExpression());
            cronDialog.close();
        });
        layout.add(saveButton);
        cronDialog.add(layout);
    }

    /**
     * Sestavi cron izraz iz trenutnih vrednosti ComboBoxov.
     */
    private String buildCronExpression() {
        return String.join(" ",
                safeValue(secondBox),
                safeValue(minuteBox),
                safeValue(hourBox),
                safeValue(dayOfMonthBox),
                safeValue(monthBox),
                safeValue(dayOfWeekBox)
        );
    }

    private String safeValue(ComboBox<String> box) {
        return box.getValue() != null ? box.getValue() : "*";
    }

    /**
     * Posodobi modelno vrednost in sproži validacijo.
     */
    public void updateValue() {
        setModelValue(buildCronExpression(), true);
    }

    @Override
    protected String generateModelValue() {
        return buildCronExpression();
    }

    /**
     * Če je zunanja vrednost nastavljena preko setValue(), razbije cron izraz in nastavi vrednosti ComboBoxov.
     */
    @Override
    protected void setPresentationValue(String cronExpression) {
        if (cronExpression == null || cronExpression.isBlank()) {
            secondBox.setValue("0");
            minuteBox.setValue("0/10");
            hourBox.setValue("*");
            dayOfMonthBox.setValue("*");
            monthBox.setValue("*");
            dayOfWeekBox.setValue("?");
            return;
        }
        String[] parts = cronExpression.split("\\s+");
        if (parts.length == 6) {
            secondBox.setValue(parts[0]);
            minuteBox.setValue(parts[1]);
            hourBox.setValue(parts[2]);
            dayOfMonthBox.setValue(parts[3]);
            monthBox.setValue(parts[4]);
            dayOfWeekBox.setValue(parts[5]);
        } else {
            secondBox.setValue("*");
            minuteBox.setValue("*");
            hourBox.setValue("*");
            dayOfMonthBox.setValue("*");
            monthBox.setValue("*");
            dayOfWeekBox.setValue("?");
        }
    }
}
