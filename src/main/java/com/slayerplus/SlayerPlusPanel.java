package com.slayerplus;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

public class SlayerPlusPanel extends PluginPanel {
  private static final Color BG = new Color(23, 23, 25);
  private static final Color PANEL = new Color(31, 31, 34);
  private static final Color SURFACE = new Color(27, 27, 30);
  private static final Color LINE = new Color(55, 55, 60);
  private static final Color SURFACE_BORDER = new Color(47, 47, 52);
  private static final Color TEXT = new Color(240, 240, 242);
  private static final Color MUTED = new Color(157, 157, 163);
  private static final Color ACCENT = new Color(246, 164, 61);
  private static final Color BLUE = new Color(57, 111, 181);
  private static final Color TAB_BG = new Color(41, 41, 45);
  private static final Color DISABLED_BG = new Color(58, 58, 62);
  private static final Color STOP_RED = new Color(153, 60, 64);
  private static final Color READY_GREEN = new Color(103, 202, 119);
  private static final int OUTER_PANEL_PADDING = 10;
  private static final int CARD_PADDING = 12;
  private static final int SECTION_PADDING = 10;
  private static final int CARD_GAP = 8;
  private static final int SECTION_GAP = 5;
  private static final int CARD_HEADING_SIZE = 10;
  private static final int SECTION_HEADING_SIZE = 9;
  private static final int BODY_TEXT_SIZE = 11;
  private static final int CONTENT_TITLE_SIZE = 15;
  private static final int INITIAL_WRAP_WIDTH = 156;
  private static final int MIN_RESPONSIVE_WRAP_WIDTH = 48;
  private static final int COMPACT_VALUE_WRAP_WIDTH = 92;
  private static final String WRAP_TEXT_PROPERTY = "slayerplus.wrapText";
  private static final String WRAP_WIDTH_CAP_PROPERTY = "slayerplus.wrapWidthCap";
  private static final String WRAP_WIDTH_PROPERTY = "slayerplus.wrapRenderedWidth";
  private static final String BANK_VIEW = "bank";
  private static final String SETTINGS_VIEW = "settings";
  static final String DISCORD_INVITE_URL = "https://discord.gg/WZCCPsTU67";
  private final JLabel accountNameValue = new JLabel("NOT LOGGED IN");
  private final PlayerPortraitPanel portraitPanel = new PlayerPortraitPanel();
  private final JLabel assignment = value("No active task", CONTENT_TITLE_SIZE);
  private final JLabel taskProgress = muted("0 remaining");
  private final JLabel pointBoostStatus = muted("");
  private final JLabel masterValue = value("Unknown", 12);
  private final JLabel pointsValue = value("0", 17);
  private final JLabel streakValue = value("0", 17);
  private final JLabel assignedAreaValue = value("—", 12);
  private final JPanel assignedAreaRow = standardRow("Assigned area", assignedAreaValue);
  private final JPanel taskCard;
  private final Runnable bankTagAction;
  private final Consumer<TaskVariant> bankVariantAction;
  private final Runnable travelHighlightToggleAction;
  private final JButton highlightButton = new RoundedButton("Start Session", 8);
  private boolean highlightActive;
  private final CardLayout viewLayout = new CardLayout();
  private final JPanel viewCards = new JPanel(viewLayout);
  private final JPanel bankTagCard;
  private final JPanel settingsCard;
  private String activeView = BANK_VIEW;
  private final CardLayout actionLayout = new CardLayout();
  private final JPanel actionCards = new JPanel(actionLayout);
  private final JButton bankViewButton = new RoundedButton("Bank Tag", 8);
  private final JButton settingsViewButton = new RoundedButton("Settings", 8);
  private final JButton tagButton = new RoundedButton("Create or update bank tag", 8);
  private final JButton discordButton = new RoundedButton("Report a bug on Discord", 8);
  private final JLabel bankLayoutTitle = value("Waiting for task", CONTENT_TITLE_SIZE);
  private final JLabel bankLayoutSubtitle = muted("");
  private final JPanel preparationPanel = new RoundedPanel(SURFACE, SURFACE_BORDER, 8);
  private final JLabel preparationValue = detailValue("");
  private final JLabel bankPreparationNote = muted("");
  private final JPanel dartPanel = new RoundedPanel(SURFACE, SURFACE_BORDER, 8);
  private final JLabel dartValue = detailValue("");
  private final JLabel dartNote = muted("");
  private final JPanel taskVariantPanel = new JPanel();
  private final JComboBox<String> taskVariantSelector = new JComboBox<>();
  private final List<TaskVariant> visibleTaskVariants = new ArrayList<>();
  private boolean updatingTaskVariant;
  private boolean bankTagReady;
  private final SlayerPlusConfig config;
  private final ConfigManager configs;
  private boolean updatingSettings;
  private JComboBox<Preference.Workflow> workflowSetting;
  private JComboBox<Preference.BonusMaster> bonusMasterSetting;
  private JPanel bonusMasterRow;
  private JLabel pointBoostExplanation;
  private JPanel pointBoostControls;
  private JComboBox<Preference.CombatStyle> combatStyleSetting;
  private JComboBox<String> helmSetting;
  private final List<String> ownedHelms = new ArrayList<>();
  private JComboBox<Preference.Cannon> cannonSetting;
  private JComboBox<Preference.Burst> burstSetting;
  private JComboBox<Preference.Shard> shardSetting;
  private final JComboBox<String> devPreviewSetting = new JComboBox<>();
  private Consumer<String> devPreviewAction;
  private boolean updatingDevPreview;
  private boolean responsiveRewrapScheduled;
  private int layoutUpdateDepth;
  private boolean layoutRefreshPending;

  public SlayerPlusPanel() {
    this(null, null, null, null, null);
  }

  public SlayerPlusPanel(
      Runnable bankTagAction,
      Consumer<TaskVariant> bankVariantAction,
      Runnable travelHighlightToggleAction,
      SlayerPlusConfig config,
      ConfigManager configs) {
    this.bankTagAction = bankTagAction;
    this.bankVariantAction = bankVariantAction;
    this.travelHighlightToggleAction = travelHighlightToggleAction;
    this.config = config;
    this.configs = configs;
    setLayout(new BorderLayout());
    setBackground(BG);
    setBorder(
        BorderFactory.createEmptyBorder(
            OUTER_PANEL_PADDING, OUTER_PANEL_PADDING, OUTER_PANEL_PADDING, OUTER_PANEL_PADDING));
    addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent event) {
            scheduleResponsiveRewrap();
          }
        });
    JPanel content = new JPanel();
    content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
    content.setBackground(BG);
    content.add(fullWidth(createHeader()));
    content.add(Box.createVerticalStrut(CARD_GAP));
    content.add(fullWidth(createPlayerCard()));
    content.add(Box.createVerticalStrut(CARD_GAP));
    taskCard = createTaskCard();
    content.add(fullWidth(taskCard));
    content.add(Box.createVerticalStrut(CARD_GAP));
    content.add(fullWidth(createViewTabs()));
    content.add(Box.createVerticalStrut(CARD_GAP));
    bankTagCard = createBankTagCard();
    settingsCard = createSettingsCard();
    viewCards.setBackground(BG);
    viewCards.add(bankTagCard, BANK_VIEW);
    viewCards.add(settingsCard, SETTINGS_VIEW);
    viewCards.setAlignmentX(LEFT_ALIGNMENT);
    content.add(viewCards);
    content.add(Box.createVerticalStrut(CARD_GAP));
    actionCards.setBackground(BG);
    actionCards.add(createBankTagActionButton(), BANK_VIEW);
    actionCards.add(new JPanel(), SETTINGS_VIEW);
    actionCards.setAlignmentX(LEFT_ALIGNMENT);
    actionCards.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
    content.add(actionCards);
    content.add(Box.createVerticalStrut(CARD_GAP));
    content.add(fullWidth(createDiscordButton()));
    add(content, BorderLayout.NORTH);
    showView(BANK_VIEW);
  }

  private JButton createDiscordButton() {
    discordButton.setFont(new Font("SansSerif", Font.BOLD, 12));
    discordButton.setForeground(TEXT);
    discordButton.setBackground(TAB_BG);
    discordButton.setFocusPainted(false);
    discordButton.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
    discordButton.setPreferredSize(new Dimension(1, 33));
    discordButton.setToolTipText("Open the SlayerPlus Discord to report a bug");
    discordButton.addActionListener(event -> LinkBrowser.browse(DISCORD_INVITE_URL));
    return discordButton;
  }

  private JPanel createHeader() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBackground(BG);
    panel.setPreferredSize(new Dimension(1, 46));
    panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
    JLabel title = new JLabel("SlayerPlus");
    title.setForeground(TEXT);
    title.setFont(new Font("SansSerif", Font.BOLD, 20));
    accountNameValue.setForeground(ACCENT);
    accountNameValue.setFont(new Font("SansSerif", Font.BOLD, 10));
    panel.add(title, BorderLayout.WEST);
    panel.add(accountNameValue, BorderLayout.SOUTH);
    return panel;
  }

  private JPanel createPlayerCard() {
    JPanel card = card();
    card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
    JPanel topRow = new JPanel(new BorderLayout(10, 0));
    topRow.setOpaque(false);
    topRow.setBorder(BorderFactory.createEmptyBorder(0, 0, CARD_GAP, 0));
    topRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 153));
    topRow.setPreferredSize(new Dimension(1, 153));
    topRow.add(portraitPanel, BorderLayout.WEST);
    JPanel sideStats = new JPanel();
    sideStats.setLayout(new BoxLayout(sideStats, BoxLayout.Y_AXIS));
    sideStats.setOpaque(false);
    sideStats.setBorder(BorderFactory.createEmptyBorder(SECTION_GAP, 0, SECTION_GAP, 0));
    sideStats.add(sideStatRow("Streak", streakValue));
    sideStats.add(sideStatRow("Points", pointsValue));
    topRow.add(sideStats, BorderLayout.CENTER);
    JPanel masterRow = new JPanel(new BorderLayout(8, 0));
    masterRow.setBackground(SURFACE);
    applySectionPadding(masterRow);
    masterRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
    JLabel masterLabel = new JLabel("Slayer master");
    masterLabel.setForeground(MUTED);
    masterLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
    masterValue.setHorizontalAlignment(SwingConstants.RIGHT);
    masterRow.add(masterLabel, BorderLayout.WEST);
    masterRow.add(masterValue, BorderLayout.EAST);
    card.add(fullWidth(topRow));
    card.add(fullWidth(masterRow));
    return card;
  }

  private JPanel sideStatRow(String labelText, JLabel valueLabel) {
    JPanel row = new JPanel();
    row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
    row.setOpaque(false);
    row.setBorder(BorderFactory.createEmptyBorder(CARD_GAP, 0, CARD_GAP, 0));
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
    JLabel label = new JLabel(labelText);
    label.setForeground(MUTED);
    label.setFont(new Font("SansSerif", Font.PLAIN, 11));
    label.setAlignmentX(RIGHT_ALIGNMENT);
    valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
    valueLabel.setAlignmentX(RIGHT_ALIGNMENT);
    row.add(label);
    row.add(Box.createVerticalStrut(3));
    row.add(valueLabel);
    return row;
  }

  private JPanel separator() {
    JPanel line = new JPanel();
    line.setBackground(LINE);
    line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
    line.setPreferredSize(new Dimension(1, 1));
    line.setMinimumSize(new Dimension(1, 1));
    line.setAlignmentX(LEFT_ALIGNMENT);
    return line;
  }

  private JPanel createTaskCard() {
    JPanel card = card();
    card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
    JPanel header = new JPanel(new BorderLayout(8, 0));
    header.setOpaque(false);
    header.setAlignmentX(LEFT_ALIGNMENT);
    JLabel currentTaskHeading = heading("CURRENT TASK");
    taskProgress.setFont(new Font("SansSerif", Font.PLAIN, 10));
    taskProgress.setHorizontalAlignment(SwingConstants.RIGHT);
    header.add(currentTaskHeading, BorderLayout.WEST);
    header.add(taskProgress, BorderLayout.EAST);
    assignment.setAlignmentX(LEFT_ALIGNMENT);
    card.add(fullWidth(header));
    card.add(Box.createVerticalStrut(CARD_GAP));
    card.add(fullWidth(assignment));
    pointBoostStatus.setAlignmentX(LEFT_ALIGNMENT);
    pointBoostStatus.setForeground(ACCENT);
    pointBoostStatus.setFont(new Font("SansSerif", Font.BOLD, 11));
    pointBoostStatus.setVisible(false);
    card.add(Box.createVerticalStrut(SECTION_GAP));
    card.add(fullWidth(pointBoostStatus));
    assignedAreaRow.setVisible(false);
    card.add(fullWidth(assignedAreaRow));
    return card;
  }

  private JPanel createViewTabs() {
    JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
    buttons.setBackground(BG);
    configureViewButton(bankViewButton);
    configureViewButton(settingsViewButton);
    bankViewButton.addActionListener(event -> showView(BANK_VIEW));
    settingsViewButton.addActionListener(event -> showView(SETTINGS_VIEW));
    buttons.add(bankViewButton);
    buttons.add(settingsViewButton);
    return buttons;
  }

  private void configureViewButton(JButton button) {
    button.setFont(new Font("SansSerif", Font.BOLD, 11));
    button.setForeground(MUTED);
    button.setBackground(TAB_BG);
    button.setFocusPainted(false);
    button.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
    button.setPreferredSize(new Dimension(1, 32));
  }

  private void showView(String view) {
    boolean settings = SETTINGS_VIEW.equals(view);
    activeView = settings ? SETTINGS_VIEW : BANK_VIEW;
    viewLayout.show(viewCards, activeView);
    actionLayout.show(actionCards, activeView);
    actionCards.setVisible(!settings);
    bankViewButton.setBackground(BANK_VIEW.equals(activeView) ? BLUE : TAB_BG);
    bankViewButton.setForeground(BANK_VIEW.equals(activeView) ? TEXT : MUTED);
    settingsViewButton.setBackground(settings ? BLUE : TAB_BG);
    settingsViewButton.setForeground(settings ? TEXT : MUTED);
    if (settings) {
      refreshSettingsControls();
    }
    refreshLayout();
  }

  private JPanel createSettingsCard() {
    JPanel settings = card();
    settings.setLayout(new BoxLayout(settings, BoxLayout.Y_AXIS));
    settings.add(fullWidth(heading("SETTINGS")));
    settings.add(Box.createVerticalStrut(SECTION_GAP));
    JLabel description = muted("");
    setWrappedText(
        description, "Preferences apply immediately to your recommendation and bank tag.");
    settings.add(fullWidth(description));
    settings.add(Box.createVerticalStrut(CARD_PADDING));
    workflowSetting = new JComboBox<>(Preference.Workflow.values());
    bonusMasterSetting = new JComboBox<>(Preference.BonusMaster.values());
    combatStyleSetting = new JComboBox<>(Preference.CombatStyle.values());
    helmSetting = new JComboBox<>();
    cannonSetting = new JComboBox<>(Preference.Cannon.values());
    burstSetting = new JComboBox<>(Preference.Burst.values());
    shardSetting = new JComboBox<>(Preference.Shard.values());
    JPanel modeSection =
        settingsSection(
            "SLAYER MODE",
            "Choose the assignment workflow independently from your loadout preferences.");
    modeSection.add(fullWidth(settingRow("Mode", workflowSetting)));
    bonusMasterRow = settingRow("Every 10th task", bonusMasterSetting);
    pointBoostExplanation = muted("");
    setWrappedText(
        pointBoostExplanation,
        "Complete nine fast Turael or Aya tasks, then take the milestone task from this master.");
    pointBoostControls = new JPanel();
    pointBoostControls.setOpaque(false);
    pointBoostControls.setLayout(new BoxLayout(pointBoostControls, BoxLayout.Y_AXIS));
    pointBoostControls.add(Box.createVerticalStrut(CARD_GAP));
    pointBoostControls.add(fullWidth(bonusMasterRow));
    pointBoostControls.add(Box.createVerticalStrut(SECTION_GAP));
    pointBoostControls.add(fullWidth(pointBoostExplanation));
    modeSection.add(fullWidth(pointBoostControls));
    settings.add(fullWidth(modeSection));
    settings.add(Box.createVerticalStrut(SECTION_PADDING));
    JPanel loadoutSection =
        settingsSection(
            "LOADOUT PREFERENCES",
            "Controls how valid gear, supplies, and task methods are ranked.");
    loadoutSection.add(fullWidth(settingRow("Combat style", combatStyleSetting)));
    loadoutSection.add(Box.createVerticalStrut(CARD_GAP));
    loadoutSection.add(fullWidth(settingRow("Slayer helm", helmSetting)));
    loadoutSection.add(Box.createVerticalStrut(CARD_GAP));
    loadoutSection.add(fullWidth(settingRow("Cannon", cannonSetting)));
    loadoutSection.add(Box.createVerticalStrut(CARD_GAP));
    loadoutSection.add(fullWidth(settingRow("Burst / barrage", burstSetting)));
    loadoutSection.add(Box.createVerticalStrut(CARD_GAP));
    loadoutSection.add(fullWidth(settingRow("Shard preference", shardSetting)));
    settings.add(fullWidth(loadoutSection));
    settings.add(Box.createVerticalStrut(SECTION_PADDING));
    JPanel devSection =
        settingsSection(
            "DEVELOPER PREVIEW",
            "Simulate any task or boss to test its loadout and routing without being assigned it.");
    devSection.add(fullWidth(settingRow("Preview task", devPreviewSetting)));
    settings.add(fullWidth(devSection));
    settings.add(Box.createVerticalGlue());
    devPreviewSetting.addActionListener(
        event -> {
          if (updatingDevPreview || devPreviewAction == null) {
            return;
          }
          Object selected = devPreviewSetting.getSelectedItem();
          devPreviewAction.accept(selected == null ? "" : selected.toString());
        });
    workflowSetting.addActionListener(
        event -> {
          saveSetting("slayerWorkflow", workflowSetting.getSelectedItem());
          refreshPointBoostSettingEnabled();
        });
    bonusMasterSetting.addActionListener(
        event -> saveSetting("pointBoostBonusMaster", bonusMasterSetting.getSelectedItem()));
    combatStyleSetting.addActionListener(
        event -> saveSetting("combatStylePreference", combatStyleSetting.getSelectedItem()));
    helmSetting.addActionListener(
        event -> saveSetting(HelmetPreference.CONFIG_KEY, helmSetting.getSelectedItem()));
    cannonSetting.addActionListener(
        event -> saveSetting("cannonPreference", cannonSetting.getSelectedItem()));
    burstSetting.addActionListener(
        event -> saveSetting("burstPreference", burstSetting.getSelectedItem()));
    shardSetting.addActionListener(
        event -> saveSetting("shardPreference", shardSetting.getSelectedItem()));
    refreshSettingsControls();
    return settings;
  }

  private void refreshPointBoostSettingEnabled() {
    if (workflowSetting == null || bonusMasterSetting == null) {
      return;
    }
    boolean pointBoosting =
        workflowSetting.getSelectedItem() == Preference.Workflow.TURAEL_POINT_BOOST;
    bonusMasterSetting.setEnabled(pointBoosting);
    if (pointBoostControls != null) {
      pointBoostControls.setVisible(pointBoosting);
      if (pointBoostControls.getParent() != null) {
        pointBoostControls.getParent().revalidate();
        pointBoostControls.getParent().repaint();
      }
      refreshLayout();
    }
  }

  private JPanel settingRow(String labelText, JComboBox<?> selector) {
    JPanel row = new JPanel(new BorderLayout(6, 4));
    row.setOpaque(false);
    JLabel label = muted(labelText);
    selector.setBackground(TAB_BG);
    selector.setForeground(TEXT);
    selector.setMaximumRowCount(8);
    selector.setFont(new Font("SansSerif", Font.PLAIN, BODY_TEXT_SIZE));
    selector.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SURFACE_BORDER),
            BorderFactory.createEmptyBorder(1, 4, 1, 4)));
    row.add(label, BorderLayout.NORTH);
    row.add(selector, BorderLayout.CENTER);
    return row;
  }

  private JPanel settingsSection(String title, String description) {
    JPanel section = new RoundedPanel(SURFACE, SURFACE_BORDER, 8);
    section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
    section.setBorder(
        BorderFactory.createEmptyBorder(
            SECTION_PADDING, SECTION_PADDING, SECTION_PADDING, SECTION_PADDING));
    section.add(fullWidth(heading(title)));
    if (description != null && !description.trim().isEmpty()) {
      section.add(Box.createVerticalStrut(SECTION_GAP));
      JLabel help = muted("");
      setWrappedText(help, description);
      section.add(fullWidth(help));
      section.add(Box.createVerticalStrut(CARD_GAP));
    }
    return section;
  }

  private void saveSetting(String key, Object value) {
    if (updatingSettings || configs == null || value == null) {
      return;
    }
    configs.setConfiguration(SlayerPlusConfig.GROUP, key, value);
  }

  private void refreshSettingsControls() {
    if (config == null || combatStyleSetting == null) {
      return;
    }
    updatingSettings = true;
    try {
      workflowSetting.setSelectedItem(config.slayerWorkflow());
      bonusMasterSetting.setSelectedItem(config.pointBoostBonusMaster());
      refreshPointBoostSettingEnabled();
      combatStyleSetting.setSelectedItem(config.combatStylePreference());
      rebuildSlayerHelmetSetting();
      cannonSetting.setSelectedItem(config.cannonPreference());
      burstSetting.setSelectedItem(config.burstPreference());
      shardSetting.setSelectedItem(config.shardPreference());
    } finally {
      updatingSettings = false;
    }
  }

  public void setDevPreviewOptions(List<String> taskNames, Consumer<String> action) {
    devPreviewAction = action;
    updatingDevPreview = true;
    try {
      devPreviewSetting.removeAllItems();
      devPreviewSetting.addItem("");
      if (taskNames != null) {
        for (String name : taskNames) {
          devPreviewSetting.addItem(name);
        }
      }
    } finally {
      updatingDevPreview = false;
    }
  }

  public void configureOwnedSlayerHelmets(List<String> helmetNames) {
    if (!ownedHelms.equals(helmetNames == null ? java.util.Collections.emptyList() : helmetNames)) {
      ownedHelms.clear();
      if (helmetNames != null) {
        ownedHelms.addAll(helmetNames);
      }
    }
    if (helmSetting != null) {
      updatingSettings = true;
      try {
        rebuildSlayerHelmetSetting();
      } finally {
        updatingSettings = false;
      }
    }
  }

  private void rebuildSlayerHelmetSetting() {
    if (helmSetting == null) {
      return;
    }
    String configured =
        configs == null
            ? HelmetPreference.COMBAT_ACHIEVEMENT
            : HelmetPreference.normalize(
                configs.getConfiguration(SlayerPlusConfig.GROUP, HelmetPreference.CONFIG_KEY));
    boolean sameOptions = helmSetting.getItemCount() == ownedHelms.size() + 2;
    for (int i = 0; sameOptions && i < ownedHelms.size(); i++) {
      sameOptions = ownedHelms.get(i).equals(helmSetting.getItemAt(i + 2));
    }
    if (!sameOptions) {
      helmSetting.removeAllItems();
      helmSetting.addItem(HelmetPreference.COMBAT_ACHIEVEMENT);
      helmSetting.addItem(HelmetPreference.RANDOM_OWNED);
      for (String helmetName : ownedHelms) {
        helmSetting.addItem(helmetName);
      }
    }
    Object selected =
        ownedHelms.contains(configured) || HelmetPreference.isRandom(configured)
            ? configured
            : HelmetPreference.COMBAT_ACHIEVEMENT;
    if (!java.util.Objects.equals(selected, helmSetting.getSelectedItem())) {
      helmSetting.setSelectedItem(selected);
    }
  }

  private JPanel createBankTagCard() {
    JPanel bankCard = card();
    bankCard.setLayout(new BoxLayout(bankCard, BoxLayout.Y_AXIS));
    bankLayoutTitle.setAlignmentX(LEFT_ALIGNMENT);
    bankLayoutSubtitle.setAlignmentX(LEFT_ALIGNMENT);
    bankCard.add(fullWidth(heading("BANK TAG SETUP")));
    bankCard.add(Box.createVerticalStrut(CARD_GAP));
    bankCard.add(fullWidth(bankLayoutTitle));
    bankCard.add(Box.createVerticalStrut(SECTION_GAP));
    bankCard.add(fullWidth(bankLayoutSubtitle));
    bankCard.add(Box.createVerticalStrut(CARD_GAP));
    bankCard.add(fullWidth(separator()));
    bankCard.add(Box.createVerticalStrut(CARD_GAP));
    bankCard.add(fullWidth(createTaskVariantSelector()));
    bankCard.add(Box.createVerticalStrut(CARD_GAP));
    bankCard.add(Box.createVerticalStrut(CARD_GAP));
    bankCard.add(fullWidth(createBankPreparationPanel()));
    bankCard.add(Box.createVerticalStrut(CARD_GAP));
    bankCard.add(fullWidth(createBankDartRecommendationPanel()));
    bankCard.add(Box.createVerticalGlue());
    return bankCard;
  }

  private JPanel createBankPreparationPanel() {
    return configurePreparationPanel(
        preparationPanel, preparationValue, bankPreparationNote, "Spell setup");
  }

  private JPanel configurePreparationPanel(
      JPanel panel, JLabel valueLabel, JLabel noteLabel, String headingText) {
    valueLabel.setFont(new Font("SansSerif", Font.BOLD, BODY_TEXT_SIZE));
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBackground(SURFACE);
    applySectionPadding(panel);
    panel.setAlignmentX(LEFT_ALIGNMENT);
    JLabel heading = sectionHeading(headingText);
    valueLabel.setAlignmentX(LEFT_ALIGNMENT);
    noteLabel.setAlignmentX(LEFT_ALIGNMENT);
    panel.add(fullWidth(heading));
    panel.add(Box.createVerticalStrut(SECTION_GAP));
    panel.add(fullWidth(valueLabel));
    panel.add(Box.createVerticalStrut(SECTION_GAP));
    panel.add(fullWidth(noteLabel));
    panel.setVisible(false);
    return panel;
  }

  private JPanel createBankDartRecommendationPanel() {
    dartPanel.setLayout(new BoxLayout(dartPanel, BoxLayout.Y_AXIS));
    dartPanel.setBackground(SURFACE);
    applySectionPadding(dartPanel);
    dartPanel.setAlignmentX(LEFT_ALIGNMENT);
    JLabel heading = sectionHeading("Darts");
    dartValue.setAlignmentX(LEFT_ALIGNMENT);
    dartNote.setAlignmentX(LEFT_ALIGNMENT);
    setOptionalText(dartValue, "");
    setOptionalText(dartNote, "");
    dartPanel.add(fullWidth(heading));
    dartPanel.add(Box.createVerticalStrut(SECTION_GAP));
    dartPanel.add(fullWidth(dartValue));
    dartPanel.add(Box.createVerticalStrut(SECTION_GAP));
    dartPanel.add(fullWidth(dartNote));
    dartPanel.setVisible(false);
    return dartPanel;
  }

  private JPanel createTaskVariantSelector() {
    taskVariantPanel.setLayout(new BorderLayout(0, 4));
    taskVariantPanel.setOpaque(false);
    taskVariantPanel.setAlignmentX(LEFT_ALIGNMENT);
    JLabel label = sectionHeading("Encounter");
    taskVariantSelector.setBackground(TAB_BG);
    taskVariantSelector.setForeground(TEXT);
    taskVariantSelector.setFont(new Font("SansSerif", Font.PLAIN, 11));
    taskVariantSelector.setFocusable(false);
    taskVariantSelector.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SURFACE_BORDER),
            BorderFactory.createEmptyBorder(2, 5, 2, 5)));
    taskVariantSelector.setPreferredSize(new Dimension(1, 29));
    taskVariantSelector.addActionListener(
        event -> {
          if (updatingTaskVariant || bankVariantAction == null) {
            return;
          }
          int index = taskVariantSelector.getSelectedIndex();
          if (index < 0 || index >= visibleTaskVariants.size()) {
            return;
          }
          bankTagReady = false;
          tagButton.setText("Refreshing setup…");
          tagButton.setEnabled(false);
          tagButton.setToolTipText("Rebuilding the bank layout for the selected encounter.");
          bankVariantAction.accept(visibleTaskVariants.get(index));
        });
    taskVariantPanel.add(label, BorderLayout.NORTH);
    taskVariantPanel.add(taskVariantSelector, BorderLayout.CENTER);
    taskVariantPanel.setVisible(false);
    return taskVariantPanel;
  }

  public void configureTaskVariants(String assignment, TaskVariant selectedVariant) {
    List<TaskVariant> variants = VariantCatalog.getAvailableVariants(assignment);
    boolean sameOptions =
        visibleTaskVariants.equals(variants)
            && taskVariantSelector.getItemCount() == variants.size();
    for (int i = 0; sameOptions && i < variants.size(); i++) {
      sameOptions =
          VariantCatalog.getOptionLabel(assignment, variants.get(i))
              .equals(taskVariantSelector.getItemAt(i));
    }
    int selectedIndex = Math.max(0, variants.indexOf(selectedVariant));
    if (sameOptions && taskVariantSelector.getSelectedIndex() == selectedIndex) {
      return;
    }
    updatingTaskVariant = true;
    try {
      if (!sameOptions) {
        visibleTaskVariants.clear();
        taskVariantSelector.removeAllItems();
        for (TaskVariant variant : variants) {
          visibleTaskVariants.add(variant);
          taskVariantSelector.addItem(VariantCatalog.getOptionLabel(assignment, variant));
        }
      }
      taskVariantSelector.setSelectedIndex(selectedIndex);
      taskVariantPanel.setVisible(variants.size() > 1);
    } finally {
      updatingTaskVariant = false;
    }
    refreshLayout();
  }

  private JPanel createBankTagActionButton() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.setBackground(BG);
    tagButton.setFont(new Font("SansSerif", Font.BOLD, 13));
    tagButton.setForeground(TEXT);
    tagButton.setBackground(DISABLED_BG);
    tagButton.setFocusPainted(false);
    tagButton.setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 10));
    tagButton.setPreferredSize(new Dimension(1, 35));
    tagButton.setEnabled(false);
    tagButton.addActionListener(
        event -> {
          if (bankTagAction == null || !bankTagReady) {
            return;
          }
          tagButton.setText("Creating…");
          tagButton.setEnabled(false);
          bankTagAction.run();
        });
    panel.add(fullWidth(tagButton));
    panel.add(Box.createVerticalStrut(SECTION_GAP));
    highlightButton.setFont(new Font("SansSerif", Font.BOLD, 13));
    highlightButton.setForeground(TEXT);
    highlightButton.setBackground(BLUE);
    highlightButton.setFocusPainted(false);
    highlightButton.setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 10));
    highlightButton.setPreferredSize(new Dimension(1, 35));
    highlightButton.setToolTipText(
        "Highlight the teleport item and destination for your current task.");
    highlightButton.addActionListener(
        event -> {
          if (travelHighlightToggleAction == null) {
            return;
          }
          travelHighlightToggleAction.run();
        });
    panel.add(fullWidth(highlightButton));
    return panel;
  }

  public void showTravelHighlightState(boolean active) {
    highlightActive = active;
    highlightButton.setText(active ? "Stop Session" : "Start Session");
    highlightButton.setBackground(active ? STOP_RED : BLUE);
  }

  private void updateDartRecommendation(Recommendation recommendation, KitPlan loadout) {
    boolean profitBlowpipe =
        recommendation != null
            && recommendation.getStrategy() != null
            && recommendation.getStrategy().getCostPolicy() == TaskStrategy.CostPolicy.EFFICIENT
            && loadout != null
            && containsLoadoutItem(loadout.getEquipmentItems(), "blowpipe");
    if (!profitBlowpipe) {
      dartPanel.setVisible(false);
      setOptionalText(dartValue, "");
      setOptionalText(dartNote, "");
      return;
    }
    String ownedDartName = findLoadoutItemName(loadout.getOptionalItems(), "dart");
    String recommendedDarts;
    boolean recommendedStackOwned;
    if (!ownedDartName.isEmpty() && !ownedDartName.toLowerCase().contains("dragon dart")) {
      recommendedDarts = pluralizeDartName(ownedDartName);
      recommendedStackOwned = true;
    } else {
      recommendedDarts = "Amethyst darts";
      recommendedStackOwned = false;
    }
    setOptionalText(dartValue, recommendedDarts);
    setOptionalText(dartNote, recommendedStackOwned ? "" : "Not currently owned");
    dartPanel.setVisible(true);
  }

  private void setBankLayoutHeading(String layoutTitle) {
    String safeTitle = layoutTitle == null ? "" : layoutTitle.trim();
    int separatorIndex = safeTitle.indexOf(" • ");
    if (separatorIndex > 0) {
      setWrappedText(
          bankLayoutTitle,
          SlayerDisplayText.bankSetupTitle(safeTitle.substring(0, separatorIndex).trim()));
      setOptionalText(bankLayoutSubtitle, safeTitle.substring(separatorIndex + 3).trim());
    } else {
      setWrappedText(bankLayoutTitle, SlayerDisplayText.bankSetupTitle(safeTitle));
      setOptionalText(bankLayoutSubtitle, "");
    }
  }

  private void updateBankTagCard(KitPlan plan) {
    KitPlan safePlan = plan == null ? KitPlan.empty() : plan;
    setBankLayoutHeading(safePlan.getLayoutTitle());
    bankTagReady = safePlan.hasConcreteItems() && bankTagAction != null;
    if (bankTagReady) {
      tagButton.setToolTipText("Create or replace the SlayerPlus Current bank tag");
      tagButton.setText("Create or update bank tag");
      tagButton.setBackground(BLUE);
      tagButton.setEnabled(true);
    } else {
      String unavailableStatus =
          safePlan.hasVisualLayout()
              ? "Open your bank once to finish this setup."
              : compactBankTagStatus(safePlan.getOwnedStatus());
      tagButton.setToolTipText(unavailableStatus);
      tagButton.setText("Bank tag unavailable");
      tagButton.setBackground(DISABLED_BG);
      tagButton.setEnabled(false);
    }
  }

  private JPanel card() {
    JPanel panel = new RoundedPanel(PANEL, LINE, 10);
    panel.setBorder(
        BorderFactory.createEmptyBorder(CARD_PADDING, CARD_PADDING, CARD_PADDING, CARD_PADDING));
    panel.setAlignmentX(LEFT_ALIGNMENT);
    panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
    return panel;
  }

  private static void applySectionPadding(JPanel panel) {
    panel.setBorder(
        BorderFactory.createEmptyBorder(
            SECTION_PADDING, SECTION_PADDING, SECTION_PADDING, SECTION_PADDING));
  }

  private JPanel standardRow(String name, JLabel value) {
    JPanel panel = new JPanel(new BorderLayout(8, 0));
    panel.setBackground(PANEL);
    panel.setBorder(BorderFactory.createEmptyBorder(8, 2, 8, 2));
    panel.add(muted(name), BorderLayout.WEST);
    value.setHorizontalAlignment(SwingConstants.RIGHT);
    panel.add(value, BorderLayout.EAST);
    return panel;
  }

  private static <T extends java.awt.Component> T fullWidth(T component) {
    if (component instanceof javax.swing.JComponent) {
      javax.swing.JComponent swingComponent = (javax.swing.JComponent) component;
      swingComponent.setAlignmentX(LEFT_ALIGNMENT);
      Dimension preferred = swingComponent.getPreferredSize();
      swingComponent.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
    }
    return component;
  }

  private static JLabel heading(String text) {
    JLabel label = new JLabel(normalizeHeading(text));
    label.setForeground(ACCENT);
    label.setFont(new Font("SansSerif", Font.BOLD, CARD_HEADING_SIZE));
    return label;
  }

  private static JLabel sectionHeading(String text) {
    JLabel label = new JLabel(normalizeHeading(text));
    label.setForeground(MUTED);
    label.setFont(new Font("SansSerif", Font.BOLD, SECTION_HEADING_SIZE));
    return label;
  }

  private static String normalizeHeading(String text) {
    return text == null ? "" : text.trim().toUpperCase(Locale.ENGLISH);
  }

  private static JLabel value(String text, int size) {
    JLabel label = new JLabel(text);
    label.setForeground(TEXT);
    label.setFont(new Font("SansSerif", Font.BOLD, size));
    return label;
  }

  private static JLabel detailValue(String text) {
    JLabel label = new JLabel(text);
    label.setForeground(TEXT);
    label.setFont(new Font("SansSerif", Font.PLAIN, BODY_TEXT_SIZE));
    return label;
  }

  private static JLabel muted(String text) {
    JLabel label = new JLabel(text);
    label.setForeground(MUTED);
    label.setFont(new Font("SansSerif", Font.PLAIN, BODY_TEXT_SIZE));
    return label;
  }

  private static void setWrappedText(JLabel label, String text) {
    setWrappedText(label, text, 0);
  }

  private static void setWrappedText(JLabel label, String text, int wrapWidthCap) {
    String safeText = text == null || text.trim().isEmpty() ? "—" : text;
    label.putClientProperty(WRAP_TEXT_PROPERTY, safeText);
    label.putClientProperty(WRAP_WIDTH_CAP_PROPERTY, Math.max(0, wrapWidthCap));
    int wrapWidth = responsiveWrapWidth(label, wrapWidthCap);
    label.putClientProperty(WRAP_WIDTH_PROPERTY, wrapWidth);
    label.setText(buildWrappedHtml(label, safeText, wrapWidth));
    label.setToolTipText(safeText);
    updateDynamicLabelHeight(label);
  }

  private static int responsiveWrapWidth(JLabel label, int requestedCap) {
    int availableWidth = 0;
    Container parent = label.getParent();
    if (parent != null && parent.getWidth() > 0) {
      Insets parentInsets = parent.getInsets();
      availableWidth = parent.getWidth() - parentInsets.left - parentInsets.right;
    }
    if (availableWidth <= 0 && label.getWidth() > 0) {
      availableWidth = label.getWidth();
    }
    if (availableWidth <= 0) {
      availableWidth = INITIAL_WRAP_WIDTH;
    }
    Insets labelInsets = label.getInsets();
    availableWidth -= labelInsets.left + labelInsets.right;
    if (requestedCap > 0) {
      availableWidth = Math.min(availableWidth, requestedCap);
    }
    return Math.max(MIN_RESPONSIVE_WRAP_WIDTH, availableWidth);
  }

  private static String buildWrappedHtml(JLabel label, String text, int wrapWidth) {
    java.awt.FontMetrics metrics = label.getFontMetrics(label.getFont());
    List<String> lines = wrapText(metrics, text, wrapWidth);
    StringBuilder html = new StringBuilder("<html>");
    boolean bold = label.getFont() != null && label.getFont().isBold();
    if (bold) {
      html.append("<b>");
    }
    for (int index = 0; index < lines.size(); index++) {
      if (index > 0) {
        html.append("<br>");
      }
      html.append(escapeHtml(lines.get(index)));
    }
    if (bold) {
      html.append("</b>");
    }
    return html.append("</html>").toString();
  }

  private static List<String> wrapText(
      java.awt.FontMetrics metrics, String text, int requestedWidth) {
    int width = Math.max(1, requestedWidth);
    List<String> lines = new ArrayList<>();
    StringBuilder line = new StringBuilder();
    String[] words = text.trim().split("\\s+");
    for (String word : words) {
      String candidate = line.length() == 0 ? word : line + " " + word;
      if (metrics.stringWidth(candidate) <= width) {
        line.setLength(0);
        line.append(candidate);
        continue;
      }
      if (line.length() > 0) {
        lines.add(line.toString());
        line.setLength(0);
      }
      if (metrics.stringWidth(word) <= width) {
        line.append(word);
        continue;
      }
      StringBuilder fragment = new StringBuilder();
      for (int index = 0; index < word.length(); index++) {
        char character = word.charAt(index);
        if (fragment.length() > 0 && metrics.stringWidth(fragment.toString() + character) > width) {
          lines.add(fragment.toString());
          fragment.setLength(0);
        }
        fragment.append(character);
      }
      line.append(fragment);
    }
    if (line.length() > 0) {
      lines.add(line.toString());
    }
    if (lines.isEmpty()) {
      lines.add("");
    }
    return lines;
  }

  private static void updateDynamicLabelHeight(JLabel label) {
    Dimension preferred = label.getPreferredSize();
    label.setMinimumSize(new Dimension(0, preferred.height));
    label.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
    label.revalidate();
  }

  private static String escapeHtml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private static String pluralizeDartName(String dartName) {
    if (dartName == null) {
      return "darts";
    }
    String trimmed = dartName.trim();
    return trimmed.toLowerCase().endsWith(" dart") ? trimmed + "s" : trimmed;
  }

  private static boolean containsLoadoutItem(List<KitItem> items, String nameFragment) {
    return !findLoadoutItemName(items, nameFragment).isEmpty();
  }

  private static String findLoadoutItemName(List<KitItem> items, String nameFragment) {
    if (items == null || nameFragment == null) {
      return "";
    }
    String fragment = nameFragment.trim().toLowerCase();
    for (KitItem item : items) {
      if (item == null || item.getDisplayName() == null) {
        continue;
      }
      String displayName = item.getDisplayName().trim();
      if (!displayName.isEmpty() && displayName.toLowerCase().contains(fragment)) {
        return displayName;
      }
    }
    return "";
  }

  private static String cleanDetail(String value) {
    if (value == null) {
      return "";
    }
    String text = value.trim();
    return text.equals("—") ? "" : text;
  }

  private static String compactPreparationHeadline(String value) {
    return cleanDetail(value)
        .replace("Ancient Magicks ready •", "Ancient Magicks •")
        .replace("Preparation required •", "Setup needed •")
        .trim();
  }

  private static String compactPreparationDetail(String value) {
    return cleanDetail(value)
        .replace("Load ", "Add ")
        .replace(" into the rune pouch", "")
        .replace("rune pouch", "pouch")
        .trim();
  }

  private static String playerFacingBankTagStatus(String value) {
    String text = compactBankTagStatus(value);
    if (text.isEmpty()) {
      return "";
    }
    String normalized = text.toLowerCase(java.util.Locale.ENGLISH);
    if (normalized.contains("opened the real slayerplus")
        || normalized.contains("arranged slots")
        || normalized.contains("unresolved recommendation")
        || normalized.contains("created")
        || normalized.contains("updated")
        || normalized.contains("replaced")) {
      return "";
    }
    if (normalized.contains("open your bank")) {
      return "Open your bank to create the layout.";
    }
    if (normalized.contains("unavailable")) {
      return "Bank Tag Layouts is unavailable.";
    }
    if (normalized.contains("teleport omitted") || normalized.contains("no teleport")) {
      return "No usable teleport was found for this route.";
    }
    return "";
  }

  private static String compactBankTagStatus(String value) {
    return cleanDetail(value)
        .replace(
            "The previously selected teleport is no longer owned, so it was omitted from the bank"
                + " tag.",
            "Teleport omitted because it is no longer owned.")
        .replace(
            "A real Bank Tags layout will be created inside your bank",
            "Open your bank once to finish this setup.")
        .trim();
  }

  private static void setOptionalText(JLabel label, String text) {
    String safeText = cleanDetail(text);
    boolean visible = !safeText.isEmpty();
    label.setVisible(visible);
    if (visible) {
      setWrappedText(label, safeText);
    } else {
      label.setText("");
      label.setToolTipText(null);
      label.putClientProperty(WRAP_TEXT_PROPERTY, null);
      label.putClientProperty(WRAP_WIDTH_CAP_PROPERTY, null);
      label.putClientProperty(WRAP_WIDTH_PROPERTY, null);
      label.revalidate();
    }
  }

  private void scheduleResponsiveRewrap() {
    if (responsiveRewrapScheduled) {
      return;
    }
    responsiveRewrapScheduled = true;
    SwingUtilities.invokeLater(
        () -> {
          responsiveRewrapScheduled = false;
          if (rewrapDynamicLabels(this)) {
            refreshLayout();
          }
        });
  }

  private static boolean rewrapDynamicLabels(Component component) {
    boolean changed = false;
    if (component instanceof JLabel) {
      JLabel label = (JLabel) component;
      Object rawText = label.getClientProperty(WRAP_TEXT_PROPERTY);
      if (rawText instanceof String) {
        Object capValue = label.getClientProperty(WRAP_WIDTH_CAP_PROPERTY);
        int cap = capValue instanceof Integer ? (Integer) capValue : 0;
        int wrapWidth = responsiveWrapWidth(label, cap);
        Object renderedWidth = label.getClientProperty(WRAP_WIDTH_PROPERTY);
        if (!(renderedWidth instanceof Integer) || (Integer) renderedWidth != wrapWidth) {
          label.putClientProperty(WRAP_WIDTH_PROPERTY, wrapWidth);
          label.setText(buildWrappedHtml(label, (String) rawText, wrapWidth));
          updateDynamicLabelHeight(label);
          changed = true;
        }
      }
    }
    if (component instanceof Container) {
      for (Component child : ((Container) component).getComponents()) {
        changed |= rewrapDynamicLabels(child);
      }
    }
    return changed;
  }

  void runBatchedUpdate(Runnable update) {
    layoutUpdateDepth++;
    try {
      update.run();
    } finally {
      layoutUpdateDepth--;
      if (layoutUpdateDepth == 0 && layoutRefreshPending) {
        layoutRefreshPending = false;
        refreshLayout();
      }
    }
  }

  private void refreshLayout() {
    if (layoutUpdateDepth > 0) {
      layoutRefreshPending = true;
      return;
    }
    refreshPanelHeight(taskCard);
    refreshPanelHeight(assignedAreaRow);
    refreshPanelHeight(taskVariantPanel);
    refreshPanelHeight(preparationPanel);
    refreshPanelHeight(dartPanel);
    updateActiveViewSize();
    revalidate();
    repaint();
    if (isDisplayable()) {
      scheduleResponsiveRewrap();
    }
  }

  private static void refreshPanelHeight(JPanel panel) {
    if (panel == null) {
      return;
    }
    panel.invalidate();
    Dimension preferred = panel.getPreferredSize();
    panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
    panel.revalidate();
  }

  void refreshResponsiveWrappingForTest() {
    if (rewrapDynamicLabels(this)) {
      refreshLayout();
    }
  }

  private void updateActiveViewSize() {
    if (bankTagCard == null || settingsCard == null) {
      return;
    }
    JPanel activeCard = SETTINGS_VIEW.equals(activeView) ? settingsCard : bankTagCard;
    int height = Math.max(1, activeCard.getPreferredSize().height);
    viewCards.setPreferredSize(new Dimension(1, height));
    viewCards.setMinimumSize(new Dimension(0, height));
    viewCards.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
  }

  public void showAccountName(String accountName) {
    String safeName =
        accountName == null || accountName.trim().isEmpty()
            ? "ACCOUNT LOADING"
            : accountName.trim();
    accountNameValue.setText(safeName);
    accountNameValue.setToolTipText(safeName);
    refreshLayout();
  }

  public void setPortrait(BufferedImage portrait) {
    portraitPanel.setPortrait(portrait);
  }

  public void disposeResources() {
    portraitPanel.disposeTimers();
  }

  public void showTask(
      String name,
      int remaining,
      int initialAmount,
      String master,
      String assignedLocation,
      int points,
      int streak) {
    showTask(
        name,
        remaining,
        initialAmount,
        master,
        assignedLocation,
        points,
        streak,
        "Konar".equalsIgnoreCase(master));
  }

  public void showTask(
      String name,
      int remaining,
      int initialAmount,
      String master,
      String assignedLocation,
      int points,
      int streak,
      boolean showAssignedArea) {
    String displayTaskName = SlayerDisplayText.assignment(name);
    setWrappedText(assignment, displayTaskName);
    taskProgress.setText(
        initialAmount > 0
            ? remaining + " / " + initialAmount + " remaining"
            : remaining + " remaining");
    masterValue.setText(master);
    pointsValue.setText(Integer.toString(points));
    streakValue.setText(Integer.toString(streak));
    setWrappedText(
        assignedAreaValue,
        showAssignedArea && assignedLocation != null ? assignedLocation : "—",
        COMPACT_VALUE_WRAP_WIDTH);
    assignedAreaRow.setVisible(showAssignedArea);
    refreshLayout();
  }

  public void showRecommendation(Recommendation recommendation, KitPlan loadout) {
    if (recommendation == null) {
      updateBankTagCard(loadout);
      updateDartRecommendation(null, loadout);
      refreshLayout();
      return;
    }
    KitPlan safeLoadout = loadout == null ? new KitPlan("No item scan available") : loadout;
    updateBankTagCard(safeLoadout);
    updateDartRecommendation(recommendation, safeLoadout);
    refreshLayout();
  }

  public void showPreparation(PreparationCatalog.PreparationPlan plan) {
    PreparationCatalog.PreparationPlan safePlan =
        plan == null ? PreparationCatalog.PreparationPlan.none() : plan;
    if (!safePlan.isActive()) {
      preparationPanel.setVisible(false);
      setOptionalText(preparationValue, "");
      setOptionalText(bankPreparationNote, "");
      refreshLayout();
      return;
    }
    Color stateColor = safePlan.isReady() ? READY_GREEN : ACCENT;
    preparationValue.setForeground(stateColor);
    String compactHeadline = compactPreparationHeadline(safePlan.getHeadline());
    String actionableDetail =
        safePlan.isReady() ? "" : compactPreparationDetail(safePlan.getDetail());
    setOptionalText(preparationValue, compactHeadline);
    setOptionalText(bankPreparationNote, actionableDetail);
    preparationPanel.setVisible(true);
    refreshLayout();
  }

  public void showBankTagStatus(String status) {
    String visibleStatus = playerFacingBankTagStatus(status);
    tagButton.setToolTipText(visibleStatus.isEmpty() ? null : visibleStatus);
    tagButton.setText("Create or update bank tag");
    tagButton.setBackground(bankTagReady ? BLUE : DISABLED_BG);
    tagButton.setEnabled(bankTagReady);
    refreshLayout();
  }

  public void showPointBoostStatus(String status) {
    setOptionalText(pointBoostStatus, status);
    refreshLayout();
  }

  public void showNoTask(int points, int streak, String master) {
    setWrappedText(assignment, "No active task");
    taskProgress.setText("");
    masterValue.setText(master);
    pointsValue.setText(Integer.toString(points));
    streakValue.setText(Integer.toString(streak));
    clearActiveTaskDisplay();
  }

  public void showLoggedOut() {
    showAccountName("NOT LOGGED IN");
    setPortrait(null);
    setWrappedText(assignment, "Not logged in");
    taskProgress.setText("");
    showPointBoostStatus("");
    masterValue.setText("—");
    pointsValue.setText("—");
    streakValue.setText("—");
    clearActiveTaskDisplay();
  }

  private void clearActiveTaskDisplay() {
    setWrappedText(assignedAreaValue, "—", COMPACT_VALUE_WRAP_WIDTH);
    assignedAreaRow.setVisible(false);
    configureTaskVariants(null, TaskVariant.STANDARD_TASK);
    updateBankTagCard(KitPlan.empty());
    updateDartRecommendation(null, KitPlan.empty());
    showPreparation(PreparationCatalog.PreparationPlan.none());
    refreshLayout();
  }

  private static final class RoundedPanel extends JPanel {
    private final Color borderColor;
    private final int arc;

    private RoundedPanel(Color fillColor, Color borderColor, int arc) {
      this.borderColor = borderColor;
      this.arc = arc;
      setBackground(fillColor);
      setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D graphics2D = (Graphics2D) graphics.create();
      try {
        graphics2D.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics2D.setColor(getBackground());
        graphics2D.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        graphics2D.setColor(borderColor);
        graphics2D.setStroke(new BasicStroke(1.0f));
        graphics2D.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
      } finally {
        graphics2D.dispose();
      }
      super.paintComponent(graphics);
    }
  }

  private static final class RoundedButton extends JButton {
    private final int arc;

    private RoundedButton(String text, int arc) {
      super(text);
      this.arc = arc;
      setOpaque(false);
      setContentAreaFilled(false);
      setBorderPainted(false);
      setRolloverEnabled(true);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      Graphics2D graphics2D = (Graphics2D) graphics.create();
      try {
        graphics2D.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color fill = getBackground();
        if (getModel().isPressed()) {
          fill = fill.darker();
        } else if (getModel().isRollover() && isEnabled()) {
          fill = fill.brighter();
        }
        graphics2D.setColor(fill);
        graphics2D.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
      } finally {
        graphics2D.dispose();
      }
      super.paintComponent(graphics);
    }
  }

  int currentTaskCardMaximumHeightForTest() {
    return taskCard.getMaximumSize().height;
  }

  int currentTaskNamePreferredHeightForTest() {
    return assignment.getPreferredSize().height;
  }

  void setCurrentTaskContainerWidthForTest(int width) {
    Container parent = assignment.getParent();
    if (parent != null) {
      parent.setSize(width, Math.max(1, parent.getHeight()));
      refreshResponsiveWrappingForTest();
    }
  }

  int currentTaskRenderedWrapWidthForTest() {
    Object width = assignment.getClientProperty(WRAP_WIDTH_PROPERTY);
    return width instanceof Integer ? (Integer) width : 0;
  }
}
