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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

public class SlayerPlusPanel extends PluginPanel {
  private static final Color BG = new Color(23, 23, 25);
  private static final Color PANEL = new Color(31, 31, 34);
  private static final Color METRIC_BG = new Color(26, 26, 29);
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
  private static final String WRAP_RENDERED_WIDTH_PROPERTY = "slayerplus.wrapRenderedWidth";
  private static final String RECOMMENDATION_VIEW = "recommendation";
  private static final String BANK_VIEW = "bank";
  private static final String SETTINGS_VIEW = "settings";
  static final String DISCORD_INVITE_URL = "https://discord.gg/WZCCPsTU67";
  private final JLabel accountNameValue = new JLabel("NOT LOGGED IN");
  private final PlayerPortraitPanel portraitPanel = new PlayerPortraitPanel();
  private final JLabel taskName = value("No active task", CONTENT_TITLE_SIZE);
  private final JLabel taskProgress = muted("0 remaining");
  private final JLabel pointBoostStatus = muted("");
  private final JLabel masterValue = value("Unknown", 12);
  private final JLabel pointsValue = value("0", 17);
  private final JLabel streakValue = value("0", 17);
  private final JLabel assignedAreaValue = value("—", 12);
  private final JPanel assignedAreaRow = standardRow("Assigned area", assignedAreaValue);
  private final JPanel taskCard;
  private final JLabel recommendationLocation = value("Waiting for task", CONTENT_TITLE_SIZE);
  private final JLabel recommendationMethod = muted("A recommendation will appear here");
  private final JLabel recommendationDetails = muted("");
  private final JPanel recommendationPositioningPanel =
      new RoundedPanel(SURFACE, SURFACE_BORDER, 8);
  private final JLabel recommendationPositioningValue = muted("");
  private final JPanel recommendationPreparationPanel =
      new RoundedPanel(SURFACE, SURFACE_BORDER, 8);
  private final JLabel recommendationPreparationValue = detailValue("");
  private final JLabel recommendationPreparationNote = muted("");
  private final JLabel routeValue = muted("");
  private final JButton actionButton = new RoundedButton("Waiting for task", 8);
  private final Runnable routeAction;
  private final Runnable bankTagAction;
  private final Consumer<TaskVariant> bankVariantAction;
  private final Runnable sessionToggleAction;
  private final CardLayout viewLayout = new CardLayout();
  private final JPanel viewCards = new JPanel(viewLayout);
  private final JPanel recommendationCard;
  private final JPanel bankTagCard;
  private final JPanel settingsCard;
  private String activeView = BANK_VIEW;
  private final CardLayout actionLayout = new CardLayout();
  private final JPanel actionCards = new JPanel(actionLayout);
  private final JButton bankViewButton = new RoundedButton("Bank Tag", 8);
  private final JButton settingsViewButton = new RoundedButton("Settings", 8);
  private final JButton bankTagButton = new RoundedButton("Create or update bank tag", 8);
  private final JButton sessionButton = new RoundedButton("Start Slayer session", 8);
  private final JButton discordButton = new RoundedButton("Report a bug on Discord", 8);
  private final JLabel bankLayoutTitle = value("Waiting for task", CONTENT_TITLE_SIZE);
  private final JLabel bankLayoutSubtitle = muted("");
  private final JPanel bankPreparationPanel = new RoundedPanel(SURFACE, SURFACE_BORDER, 8);
  private final JLabel bankPreparationValue = detailValue("");
  private final JLabel bankPreparationNote = muted("");
  private final JPanel bankPositioningPanel = new RoundedPanel(SURFACE, SURFACE_BORDER, 8);
  private final JLabel bankPositioningValue = muted("");
  private final JPanel bankTravelRecommendationPanel =
      new RoundedPanel(new Color(49, 39, 24), new Color(126, 88, 40), 8);
  private final JLabel bankTravelRecommendationValue = detailValue("");
  private final JLabel bankTravelRecommendationNote = muted("");
  private final JPanel bankDartRecommendationPanel = new RoundedPanel(SURFACE, SURFACE_BORDER, 8);
  private final JLabel bankDartRecommendationValue = detailValue("");
  private final JLabel bankDartRecommendationNote = muted("");
  private final JPanel taskVariantPanel = new JPanel();
  private final JComboBox<String> taskVariantSelector = new JComboBox<>();
  private final List<TaskVariant> visibleTaskVariants = new ArrayList<>();
  private boolean updatingTaskVariant;
  private boolean bankTagReady;
  private boolean sessionActive;
  private boolean sessionAvailable;
  private final SlayerPlusConfig config;
  private final ConfigManager configManager;
  private boolean updatingSettings;
  private JComboBox<Preference.Workflow> workflowSetting;
  private JComboBox<Preference.BonusMaster> bonusMasterSetting;
  private JPanel bonusMasterRow;
  private JLabel pointBoostExplanation;
  private JPanel pointBoostControls;
  private JComboBox<Preference.CombatStyle> combatStyleSetting;
  private JComboBox<String> slayerHelmetSetting;
  private final List<String> ownedSlayerHelmetNames = new ArrayList<>();
  private JComboBox<Preference.Playstyle> playstyleSetting;
  private JComboBox<Preference.Cannon> cannonSetting;
  private JComboBox<Preference.Burst> burstSetting;
  private JComboBox<Preference.Travel> travelSetting;
  private JComboBox<Preference.Shard> shardSetting;
  private boolean responsiveRewrapScheduled;

  public SlayerPlusPanel() {
    this(null, null, null, null);
  }

  public SlayerPlusPanel(Runnable routeAction) {
    this(routeAction, null, null, null);
  }

  public SlayerPlusPanel(Runnable routeAction, Runnable bankTagAction) {
    this(routeAction, bankTagAction, null, null);
  }

  public SlayerPlusPanel(
      Runnable routeAction, Runnable bankTagAction, Consumer<TaskVariant> bankVariantAction) {
    this(routeAction, bankTagAction, bankVariantAction, null);
  }

  public SlayerPlusPanel(
      Runnable routeAction,
      Runnable bankTagAction,
      Consumer<TaskVariant> bankVariantAction,
      Runnable sessionToggleAction) {
    this(routeAction, bankTagAction, bankVariantAction, sessionToggleAction, null, null);
  }

  public SlayerPlusPanel(
      Runnable routeAction,
      Runnable bankTagAction,
      Consumer<TaskVariant> bankVariantAction,
      Runnable sessionToggleAction,
      SlayerPlusConfig config,
      ConfigManager configManager) {
    this.routeAction = routeAction;
    this.bankTagAction = bankTagAction;
    this.bankVariantAction = bankVariantAction;
    this.sessionToggleAction = sessionToggleAction;
    this.config = config;
    this.configManager = configManager;
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
    recommendationCard = createRecommendationCard();
    bankTagCard = createBankTagCard();
    settingsCard = createSettingsCard();
    viewCards.setBackground(BG);
    viewCards.add(recommendationCard, RECOMMENDATION_VIEW);
    viewCards.add(bankTagCard, BANK_VIEW);
    viewCards.add(settingsCard, SETTINGS_VIEW);
    viewCards.setAlignmentX(LEFT_ALIGNMENT);
    content.add(viewCards);
    content.add(Box.createVerticalStrut(CARD_GAP));
    actionCards.setBackground(BG);
    actionCards.add(createActionButton(), RECOMMENDATION_VIEW);
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
    taskName.setAlignmentX(LEFT_ALIGNMENT);
    card.add(fullWidth(header));
    card.add(Box.createVerticalStrut(CARD_GAP));
    card.add(fullWidth(taskName));
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

  private JPanel createRecommendationCard() {
    JPanel card = card();
    card.setLayout(new BorderLayout());
    JPanel content = new JPanel();
    content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
    content.setOpaque(false);
    JLabel cardHeading = heading("TASK RECOMMENDATION");
    cardHeading.setAlignmentX(LEFT_ALIGNMENT);
    recommendationLocation.setAlignmentX(LEFT_ALIGNMENT);
    recommendationMethod.setAlignmentX(LEFT_ALIGNMENT);
    recommendationDetails.setAlignmentX(LEFT_ALIGNMENT);
    routeValue.setAlignmentX(LEFT_ALIGNMENT);
    recommendationDetails.setBorder(BorderFactory.createEmptyBorder(CARD_GAP, 0, 0, 0));
    routeValue.setBorder(BorderFactory.createEmptyBorder(CARD_GAP, 0, 0, 0));
    routeValue.setForeground(new Color(145, 180, 220));
    setWrappedText(recommendationLocation, "Waiting for task");
    setWrappedText(recommendationMethod, "A recommendation will appear here");
    setOptionalWrappedText(recommendationDetails, "");
    setOptionalWrappedText(routeValue, "");
    content.add(fullWidth(cardHeading));
    content.add(Box.createVerticalStrut(CARD_GAP));
    content.add(fullWidth(recommendationLocation));
    content.add(Box.createVerticalStrut(SECTION_GAP));
    content.add(fullWidth(recommendationMethod));
    content.add(Box.createVerticalStrut(CARD_GAP));
    content.add(fullWidth(createRecommendationPositioningPanel()));
    content.add(Box.createVerticalStrut(CARD_GAP));
    content.add(fullWidth(createRecommendationPreparationPanel()));
    content.add(fullWidth(recommendationDetails));
    content.add(fullWidth(routeValue));
    card.add(content, BorderLayout.NORTH);
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
    boolean recommendation = RECOMMENDATION_VIEW.equals(view);
    activeView = settings ? SETTINGS_VIEW : recommendation ? RECOMMENDATION_VIEW : BANK_VIEW;
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
    slayerHelmetSetting = new JComboBox<>();
    playstyleSetting =
        new JComboBox<>(
            new Preference.Playstyle[] {
              Preference.Playstyle.FAST_XP, Preference.Playstyle.PROFIT
            });
    cannonSetting = new JComboBox<>(Preference.Cannon.values());
    burstSetting = new JComboBox<>(Preference.Burst.values());
    travelSetting = new JComboBox<>(Preference.Travel.values());
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
    loadoutSection.add(fullWidth(settingRow("Playstyle", playstyleSetting)));
    loadoutSection.add(Box.createVerticalStrut(CARD_GAP));
    loadoutSection.add(fullWidth(settingRow("Combat style", combatStyleSetting)));
    loadoutSection.add(Box.createVerticalStrut(CARD_GAP));
    loadoutSection.add(fullWidth(settingRow("Slayer helm", slayerHelmetSetting)));
    loadoutSection.add(Box.createVerticalStrut(CARD_GAP));
    loadoutSection.add(fullWidth(settingRow("Cannon", cannonSetting)));
    loadoutSection.add(Box.createVerticalStrut(CARD_GAP));
    loadoutSection.add(fullWidth(settingRow("Burst / barrage", burstSetting)));
    loadoutSection.add(Box.createVerticalStrut(CARD_GAP));
    loadoutSection.add(fullWidth(settingRow("Shard preference", shardSetting)));
    loadoutSection.add(Box.createVerticalStrut(CARD_GAP));
    loadoutSection.add(fullWidth(settingRow("Travel priority", travelSetting)));
    settings.add(fullWidth(loadoutSection));
    settings.add(Box.createVerticalGlue());
    workflowSetting.addActionListener(
        event -> {
          saveSetting("slayerWorkflow", workflowSetting.getSelectedItem());
          refreshPointBoostSettingEnabled();
        });
    bonusMasterSetting.addActionListener(
        event -> saveSetting("pointBoostBonusMaster", bonusMasterSetting.getSelectedItem()));
    combatStyleSetting.addActionListener(
        event -> saveSetting("combatStylePreference", combatStyleSetting.getSelectedItem()));
    slayerHelmetSetting.addActionListener(
        event ->
            saveSetting(HelmetPreference.CONFIG_KEY, slayerHelmetSetting.getSelectedItem()));
    playstyleSetting.addActionListener(
        event -> saveSetting("playstyle", playstyleSetting.getSelectedItem()));
    cannonSetting.addActionListener(
        event -> saveSetting("cannonPreference", cannonSetting.getSelectedItem()));
    burstSetting.addActionListener(
        event -> saveSetting("burstPreference", burstSetting.getSelectedItem()));
    travelSetting.addActionListener(
        event -> saveSetting("travelPreference", travelSetting.getSelectedItem()));
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
    if (updatingSettings || configManager == null || value == null) {
      return;
    }
    configManager.setConfiguration(SlayerPlusConfig.GROUP, key, value);
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
      playstyleSetting.setSelectedItem(config.playstyle());
      cannonSetting.setSelectedItem(config.cannonPreference());
      burstSetting.setSelectedItem(config.burstPreference());
      travelSetting.setSelectedItem(config.travelPreference());
      shardSetting.setSelectedItem(config.shardPreference());
    } finally {
      updatingSettings = false;
    }
  }

  public void configureOwnedSlayerHelmets(List<String> helmetNames) {
    ownedSlayerHelmetNames.clear();
    if (helmetNames != null) {
      ownedSlayerHelmetNames.addAll(helmetNames);
    }
    if (slayerHelmetSetting != null) {
      updatingSettings = true;
      try {
        rebuildSlayerHelmetSetting();
      } finally {
        updatingSettings = false;
      }
    }
  }

  private void rebuildSlayerHelmetSetting() {
    if (slayerHelmetSetting == null) {
      return;
    }
    String configured =
        configManager == null
            ? HelmetPreference.COMBAT_ACHIEVEMENT
            : HelmetPreference.normalize(
                configManager.getConfiguration(
                    SlayerPlusConfig.GROUP, HelmetPreference.CONFIG_KEY));
    slayerHelmetSetting.removeAllItems();
    slayerHelmetSetting.addItem(HelmetPreference.COMBAT_ACHIEVEMENT);
    slayerHelmetSetting.addItem(HelmetPreference.RANDOM_OWNED);
    for (String helmetName : ownedSlayerHelmetNames) {
      slayerHelmetSetting.addItem(helmetName);
    }
    slayerHelmetSetting.setSelectedItem(
        ownedSlayerHelmetNames.contains(configured) || HelmetPreference.isRandom(configured)
            ? configured
            : HelmetPreference.COMBAT_ACHIEVEMENT);
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
    bankCard.add(fullWidth(createBankTravelRecommendationPanel()));
    bankCard.add(Box.createVerticalStrut(CARD_GAP));
    bankCard.add(fullWidth(createBankPreparationPanel()));
    bankCard.add(Box.createVerticalStrut(CARD_GAP));
    bankCard.add(fullWidth(createBankPositioningPanel()));
    bankCard.add(Box.createVerticalStrut(CARD_GAP));
    bankCard.add(fullWidth(createBankDartRecommendationPanel()));
    bankCard.add(Box.createVerticalGlue());
    return bankCard;
  }

  private JPanel createRecommendationPositioningPanel() {
    recommendationPositioningPanel.setLayout(
        new BoxLayout(recommendationPositioningPanel, BoxLayout.Y_AXIS));
    recommendationPositioningPanel.setBackground(SURFACE);
    applySectionPadding(recommendationPositioningPanel);
    recommendationPositioningPanel.setAlignmentX(LEFT_ALIGNMENT);
    JLabel heading = sectionHeading("Positioning");
    recommendationPositioningValue.setAlignmentX(LEFT_ALIGNMENT);
    recommendationPositioningPanel.add(fullWidth(heading));
    recommendationPositioningPanel.add(Box.createVerticalStrut(SECTION_GAP));
    recommendationPositioningPanel.add(fullWidth(recommendationPositioningValue));
    recommendationPositioningPanel.setVisible(false);
    return recommendationPositioningPanel;
  }

  private JPanel createRecommendationPreparationPanel() {
    return configurePreparationPanel(
        recommendationPreparationPanel,
        recommendationPreparationValue,
        recommendationPreparationNote,
        "Required preparation");
  }

  private JPanel createBankPreparationPanel() {
    return configurePreparationPanel(
        bankPreparationPanel, bankPreparationValue, bankPreparationNote, "Spell setup");
  }

  private JPanel createBankPositioningPanel() {
    bankPositioningPanel.setLayout(new BoxLayout(bankPositioningPanel, BoxLayout.Y_AXIS));
    bankPositioningPanel.setBackground(SURFACE);
    applySectionPadding(bankPositioningPanel);
    bankPositioningPanel.setAlignmentX(LEFT_ALIGNMENT);
    JLabel heading = heading("TASK METHOD");
    bankPositioningValue.setAlignmentX(LEFT_ALIGNMENT);
    bankPositioningPanel.add(fullWidth(heading));
    bankPositioningPanel.add(Box.createVerticalStrut(SECTION_GAP));
    bankPositioningPanel.add(fullWidth(bankPositioningValue));
    bankPositioningPanel.setVisible(false);
    return bankPositioningPanel;
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

  private JPanel createBankTravelRecommendationPanel() {
    bankTravelRecommendationPanel.setLayout(
        new BoxLayout(bankTravelRecommendationPanel, BoxLayout.Y_AXIS));
    bankTravelRecommendationPanel.setBackground(SURFACE);
    applySectionPadding(bankTravelRecommendationPanel);
    bankTravelRecommendationPanel.setAlignmentX(LEFT_ALIGNMENT);
    JLabel heading = heading("NEXT STEP");
    bankTravelRecommendationValue.setAlignmentX(LEFT_ALIGNMENT);
    bankTravelRecommendationNote.setAlignmentX(LEFT_ALIGNMENT);
    bankTravelRecommendationValue.setForeground(ACCENT);
    bankTravelRecommendationValue.setFont(new Font("SansSerif", Font.BOLD, 12));
    bankTravelRecommendationNote.setForeground(TEXT);
    setOptionalWrappedText(bankTravelRecommendationValue, "");
    setOptionalWrappedText(bankTravelRecommendationNote, "");
    bankTravelRecommendationPanel.add(fullWidth(heading));
    bankTravelRecommendationPanel.add(Box.createVerticalStrut(SECTION_GAP));
    bankTravelRecommendationPanel.add(fullWidth(bankTravelRecommendationValue));
    bankTravelRecommendationPanel.add(Box.createVerticalStrut(SECTION_GAP));
    bankTravelRecommendationPanel.add(fullWidth(bankTravelRecommendationNote));
    bankTravelRecommendationPanel.setVisible(false);
    return bankTravelRecommendationPanel;
  }

  private JPanel createBankDartRecommendationPanel() {
    bankDartRecommendationPanel.setLayout(
        new BoxLayout(bankDartRecommendationPanel, BoxLayout.Y_AXIS));
    bankDartRecommendationPanel.setBackground(SURFACE);
    applySectionPadding(bankDartRecommendationPanel);
    bankDartRecommendationPanel.setAlignmentX(LEFT_ALIGNMENT);
    JLabel heading = sectionHeading("Darts");
    bankDartRecommendationValue.setAlignmentX(LEFT_ALIGNMENT);
    bankDartRecommendationNote.setAlignmentX(LEFT_ALIGNMENT);
    setOptionalWrappedText(bankDartRecommendationValue, "");
    setOptionalWrappedText(bankDartRecommendationNote, "");
    bankDartRecommendationPanel.add(fullWidth(heading));
    bankDartRecommendationPanel.add(Box.createVerticalStrut(SECTION_GAP));
    bankDartRecommendationPanel.add(fullWidth(bankDartRecommendationValue));
    bankDartRecommendationPanel.add(Box.createVerticalStrut(SECTION_GAP));
    bankDartRecommendationPanel.add(fullWidth(bankDartRecommendationNote));
    bankDartRecommendationPanel.setVisible(false);
    return bankDartRecommendationPanel;
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
          bankTagButton.setText("Refreshing setup…");
          bankTagButton.setEnabled(false);
          bankTagButton.setToolTipText("Rebuilding the bank layout for the selected encounter.");
          bankVariantAction.accept(visibleTaskVariants.get(index));
        });
    taskVariantPanel.add(label, BorderLayout.NORTH);
    taskVariantPanel.add(taskVariantSelector, BorderLayout.CENTER);
    taskVariantPanel.setVisible(false);
    return taskVariantPanel;
  }

  public void configureTaskVariants(String taskName, TaskVariant selectedVariant) {
    List<TaskVariant> variants = VariantCatalog.getAvailableVariants(taskName);
    updatingTaskVariant = true;
    try {
      visibleTaskVariants.clear();
      taskVariantSelector.removeAllItems();
      for (TaskVariant variant : variants) {
        visibleTaskVariants.add(variant);
        taskVariantSelector.addItem(VariantCatalog.getOptionLabel(taskName, variant));
      }
      int selectedIndex = variants.indexOf(selectedVariant);
      if (selectedIndex < 0) {
        selectedIndex = 0;
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
    bankTagButton.setFont(new Font("SansSerif", Font.BOLD, 13));
    bankTagButton.setForeground(TEXT);
    bankTagButton.setBackground(DISABLED_BG);
    bankTagButton.setFocusPainted(false);
    bankTagButton.setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 10));
    bankTagButton.setPreferredSize(new Dimension(1, 35));
    bankTagButton.setEnabled(false);
    bankTagButton.addActionListener(
        event -> {
          if (bankTagAction == null || !bankTagReady) {
            return;
          }
          bankTagButton.setText("Creating…");
          bankTagButton.setEnabled(false);
          bankTagAction.run();
        });
    sessionButton.setFont(new Font("SansSerif", Font.BOLD, 13));
    sessionButton.setForeground(TEXT);
    sessionButton.setBackground(DISABLED_BG);
    sessionButton.setFocusPainted(false);
    sessionButton.setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 10));
    sessionButton.setPreferredSize(new Dimension(1, 35));
    sessionButton.setEnabled(false);
    sessionButton.addActionListener(
        event -> {
          if (sessionToggleAction == null || (!sessionActive && !sessionAvailable)) {
            return;
          }
          sessionButton.setText(sessionActive ? "Stopping…" : "Starting…");
          sessionButton.setEnabled(false);
          sessionToggleAction.run();
        });
    panel.add(fullWidth(bankTagButton));
    panel.add(Box.createVerticalStrut(7));
    panel.add(fullWidth(sessionButton));
    return panel;
  }

  private void updateBankDartRecommendation(
      Recommendation recommendation, KitPlan loadout) {
    boolean profitBlowpipe =
        recommendation != null
            && recommendation.getStrategy() != null
            && recommendation.getStrategy().getCostPolicy()
                == TaskStrategy.CostPolicy.EFFICIENT
            && loadout != null
            && containsLoadoutItem(loadout.getEquipmentItems(), "blowpipe");
    if (!profitBlowpipe) {
      bankDartRecommendationPanel.setVisible(false);
      setOptionalWrappedText(bankDartRecommendationValue, "");
      setOptionalWrappedText(bankDartRecommendationNote, "");
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
    setOptionalWrappedText(bankDartRecommendationValue, recommendedDarts);
    setOptionalWrappedText(
        bankDartRecommendationNote, recommendedStackOwned ? "" : "Not currently owned");
    bankDartRecommendationPanel.setVisible(true);
  }

  private void setBankLayoutHeading(String layoutTitle) {
    String safeTitle = layoutTitle == null ? "" : layoutTitle.trim();
    int separatorIndex = safeTitle.indexOf(" • ");
    if (separatorIndex > 0) {
      setWrappedText(
          bankLayoutTitle,
          SlayerDisplayText.bankSetupTitle(safeTitle.substring(0, separatorIndex).trim()));
      setOptionalWrappedText(bankLayoutSubtitle, safeTitle.substring(separatorIndex + 3).trim());
    } else {
      setWrappedText(bankLayoutTitle, SlayerDisplayText.bankSetupTitle(safeTitle));
      setOptionalWrappedText(bankLayoutSubtitle, "");
    }
  }

  private void updateBankTagCard(KitPlan plan) {
    KitPlan safePlan = plan == null ? KitPlan.empty() : plan;
    setBankLayoutHeading(safePlan.getLayoutTitle());
    bankTagReady = safePlan.hasConcreteItems() && bankTagAction != null;
    if (bankTagReady) {
      bankTagButton.setToolTipText("Create or replace the SlayerPlus Current bank tag");
      bankTagButton.setText("Create or update bank tag");
      bankTagButton.setBackground(BLUE);
      bankTagButton.setEnabled(true);
    } else {
      String unavailableStatus =
          safePlan.hasVisualLayout()
              ? "Open your bank once to finish this setup."
              : compactBankTagStatus(safePlan.getOwnedStatus());
      bankTagButton.setToolTipText(unavailableStatus);
      bankTagButton.setText("Bank tag unavailable");
      bankTagButton.setBackground(DISABLED_BG);
      bankTagButton.setEnabled(false);
    }
  }

  private JPanel createActionButton() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBackground(BG);
    actionButton.setFont(new Font("SansSerif", Font.BOLD, 13));
    actionButton.setForeground(TEXT);
    actionButton.setBackground(DISABLED_BG);
    actionButton.setFocusPainted(false);
    actionButton.setEnabled(false);
    actionButton.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    actionButton.addActionListener(
        event -> {
          if (routeAction != null) {
            routeAction.run();
          }
        });
    panel.add(actionButton, BorderLayout.CENTER);
    return panel;
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
    label.putClientProperty(WRAP_RENDERED_WIDTH_PROPERTY, wrapWidth);
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

  private static String buildRecommendationDetails(
      Recommendation recommendation, KitPlan loadout) {
    if (recommendation == null) {
      return "";
    }
    List<String> details = new ArrayList<>();
    addImportantCannonDetail(details, recommendation.getCannon());
    addProfitBlowpipeDartDetail(details, recommendation, loadout);
    addImportantRequirement(details, recommendation.getRequirements());
    addGlobalLoadoutRequirement(details, recommendation, loadout);
    addImportantRestriction(details, recommendation.getRestriction());
    return String.join("  •  ", details);
  }

  private static void addGlobalLoadoutRequirement(
      List<String> details, Recommendation recommendation, KitPlan loadout) {
    if (details == null
        || loadout == null
        || !containsLoadoutItem(loadout.getInventoryItems(), "rune pouch")) {
      return;
    }
    String authored =
        recommendation == null ? "" : recommendation.getRequirements().toLowerCase(Locale.ENGLISH);
    if (!authored.contains("rune pouch")) {
      details.add("Rune pouch required");
    }
  }

  private static void addProfitBlowpipeDartDetail(
      List<String> details, Recommendation recommendation, KitPlan loadout) {
    if (details == null
        || recommendation == null
        || recommendation.getStrategy() == null
        || recommendation.getStrategy().getCostPolicy() != TaskStrategy.CostPolicy.EFFICIENT
        || loadout == null
        || !containsLoadoutItem(loadout.getEquipmentItems(), "blowpipe")) {
      return;
    }
    String dartName = findLoadoutItemName(loadout.getOptionalItems(), "dart");
    String normalizedDart = dartName.toLowerCase();
    if (normalizedDart.contains("dragon dart")) {
      details.add(
          "Profit tip: Dragon darts are expensive; switch to "
              + "Amethyst, Rune, or Adamant darts to reduce "
              + "blowpipe cost");
    } else if (!dartName.isEmpty()) {
      details.add(
          "Profit tip: use "
              + pluralizeDartName(dartName)
              + " in the blowpipe to reduce ammunition cost");
    } else {
      details.add(
          "Profit tip: use an economical dart tier in the " + "blowpipe instead of Dragon darts");
    }
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

  private static void addImportantCannonDetail(List<String> details, String value) {
    String text = cleanDetail(value);
    String normalized = text.toLowerCase();
    if (normalized.equals("recommended")) {
      details.add("Cannon recommended");
    } else if (normalized.contains("preferred")) {
      details.add("Cannon preferred");
    } else if (normalized.equals("not allowed")) {
      details.add("Cannon not allowed");
    } else if (normalized.startsWith("locked")) {
      details.add("Cannon " + text.toLowerCase());
    }
  }

  private static void addImportantRequirement(List<String> details, String value) {
    String text = cleanDetail(value);
    String normalized = text.toLowerCase();
    if (text.isEmpty()
        || normalized.equals("none")
        || normalized.equals("task-specific equipment profile active")
        || normalized.equals("use the monster inside the assigned area")
        || normalized.equals("krystilia assignment active")
        || (normalized.endsWith("— completed") && !normalized.contains("unlocks"))) {
      return;
    }
    if (normalized.contains("required")
        || normalized.contains("unlocks")
        || normalized.contains("verification")
        || normalized.contains("complete ")) {
      details.add(text);
    }
  }

  private static void addImportantRestriction(List<String> details, String value) {
    String text = cleanDetail(value);
    String normalized = text.toLowerCase();
    if (text.isEmpty()
        || normalized.equals("none")
        || normalized.equals("non-wilderness recommendation")
        || normalized.equals("wilderness locations excluded")
        || normalized.equals("assigned area required")
        || normalized.equals("konar — assigned area required")) {
      return;
    }
    if (normalized.contains("wilderness")
        || normalized.contains("blocked")
        || normalized.contains("protect-item")
        || normalized.contains("locked")) {
      details.add(text);
    }
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

  private static String compactPositioningNote(String value) {
    String text = cleanDetail(value);
    if (text.isEmpty()) {
      return "";
    }
    text =
        text.replaceFirst("(?i)^At the task area[.:]?\\s*", "")
            .replace("Place the dwarf multicannon", "Place cannon")
            .replace("then stack the Smoke devils around", "Stack Smoke devils at")
            .replace("before using Ice Barrage or Ice Burst", ". Use Ice Barrage/Burst")
            .replace("near the room centre,", "near room centre.")
            .replace("around the western skeleton pile", "at the western skeleton pile")
            .replace(";", ".")
            .replaceAll("\\s+", " ")
            .replaceAll("\\.\\s*\\.", ".")
            .trim();
    return text;
  }

  private static String displayTaskMethod(Recommendation recommendation) {
    if (recommendation == null) {
      return "";
    }
    String recommended = cleanDetail(recommendation.getMethod());
    if (!recommended.isEmpty()) {
      return recommended;
    }
    if (recommendation.getStrategy() != null) {
      String authored = cleanDetail(recommendation.getStrategy().getMethod());
      if (!authored.isEmpty()) {
        return authored;
      }
    }
    return "Use the selected reviewed combat setup and follow the task's protection and positioning"
               + " guidance.";
  }

  static String compactSessionStatus(boolean active, boolean available, String value) {
    String text = cleanDetail(value);
    if (text.isEmpty()) {
      return "";
    }
    String normalized = text.toLowerCase();
    if (active && normalized.contains("ghommal") && normalized.contains("mor ul rek")) {
      return "";
    }
    if (active && normalized.contains("hot vent door") && normalized.contains("pass")) {
      return "Pass through the glowing Hot vent door. SlayerPlus will start the local Zuk-bank"
                 + " route after you cross.";
    }
    if (active
        && (normalized.startsWith("at the task area")
            || normalized.startsWith("session active")
            || normalized.startsWith("routing to")
            || normalized.contains("follow the route")
            || normalized.contains("shortest path is comparing")
            || normalized.contains("route active"))) {
      return "";
    }
    if (!active && available && normalized.contains("start a guided session")) {
      return "";
    }
    return text.replace(
            "Enable Shortest Path routing in SlayerPlus settings first",
            "Enable Shortest Path routing in settings.")
        .replace("Log in to start a guided Slayer session", "Log in to start a Slayer session.")
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

  private static void setOptionalWrappedText(JLabel label, String text) {
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
      label.putClientProperty(WRAP_RENDERED_WIDTH_PROPERTY, null);
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
        Object renderedWidth = label.getClientProperty(WRAP_RENDERED_WIDTH_PROPERTY);
        if (!(renderedWidth instanceof Integer) || (Integer) renderedWidth != wrapWidth) {
          label.putClientProperty(WRAP_RENDERED_WIDTH_PROPERTY, wrapWidth);
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

  private void refreshLayout() {
    refreshDynamicPanelHeight(taskCard);
    refreshDynamicPanelHeight(assignedAreaRow);
    refreshDynamicPanelHeight(taskVariantPanel);
    refreshDynamicPanelHeight(recommendationPositioningPanel);
    refreshDynamicPanelHeight(recommendationPreparationPanel);
    refreshDynamicPanelHeight(bankPreparationPanel);
    refreshDynamicPanelHeight(bankPositioningPanel);
    refreshDynamicPanelHeight(bankTravelRecommendationPanel);
    refreshDynamicPanelHeight(bankDartRecommendationPanel);
    updateActiveViewSize();
    revalidate();
    repaint();
    if (isDisplayable()) {
      scheduleResponsiveRewrap();
    }
  }

  private static void refreshDynamicPanelHeight(JPanel panel) {
    if (panel == null) {
      return;
    }
    panel.invalidate();
    Dimension preferred = panel.getPreferredSize();
    panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
    panel.revalidate();
  }

  int currentTaskCardMaximumHeightForRegression() {
    return taskCard.getMaximumSize().height;
  }

  int currentTaskNamePreferredHeightForRegression() {
    return taskName.getPreferredSize().height;
  }

  int bankTravelMaximumHeightForRegression() {
    return bankTravelRecommendationPanel.getMaximumSize().height;
  }

  void refreshResponsiveWrappingForRegression() {
    if (rewrapDynamicLabels(this)) {
      refreshLayout();
    }
  }

  void setCurrentTaskContainerWidthForRegression(int width) {
    Container parent = taskName.getParent();
    if (parent != null) {
      parent.setSize(width, Math.max(1, parent.getHeight()));
      refreshResponsiveWrappingForRegression();
    }
  }

  int currentTaskRenderedWrapWidthForRegression() {
    Object width = taskName.getClientProperty(WRAP_RENDERED_WIDTH_PROPERTY);
    return width instanceof Integer ? (Integer) width : 0;
  }

  private void updateActiveViewSize() {
    if (recommendationCard == null || bankTagCard == null || settingsCard == null) {
      return;
    }
    JPanel activeCard;
    if (SETTINGS_VIEW.equals(activeView)) {
      activeCard = settingsCard;
    } else if (BANK_VIEW.equals(activeView)) {
      activeCard = bankTagCard;
    } else {
      activeCard = recommendationCard;
    }
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
    String displayTaskName = SlayerDisplayText.taskName(name);
    setWrappedText(taskName, displayTaskName);
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
    setWrappedText(recommendationLocation, "Evaluating " + displayTaskName);
    setWrappedText(
        recommendationMethod, "Checking location, quest, cannon, and master restrictions");
    setOptionalWrappedText(recommendationDetails, "");
    setOptionalWrappedText(routeValue, "");
    bankPositioningPanel.setVisible(false);
    setOptionalWrappedText(bankPositioningValue, "");
    actionButton.setText("Route unavailable");
    actionButton.setBackground(DISABLED_BG);
    actionButton.setEnabled(false);
    refreshLayout();
  }

  public void showRecommendation(Recommendation recommendation) {
    showRecommendation(
        recommendation, new KitPlan("—", "—", "No item scan available"), true);
  }

  public void showRecommendation(
      Recommendation recommendation, KitPlan loadout, boolean routingEnabled) {
    if (recommendation == null) {
      updateBankTagCard(loadout);
      updateBankDartRecommendation(null, loadout);
      bankPositioningPanel.setVisible(false);
      setOptionalWrappedText(bankPositioningValue, "");
      setWrappedText(recommendationLocation, "No recommendation available");
      setWrappedText(recommendationMethod, "The recommendation engine did not return a result");
      setOptionalWrappedText(recommendationDetails, "");
      setOptionalWrappedText(routeValue, "No destination available");
      actionButton.setText("Route unavailable");
      actionButton.setBackground(DISABLED_BG);
      actionButton.setEnabled(false);
      refreshLayout();
      return;
    }
    setWrappedText(recommendationLocation, recommendation.getLocation());
    String taskMethod = displayTaskMethod(recommendation);
    setWrappedText(recommendationMethod, taskMethod);
    setOptionalWrappedText(bankPositioningValue, taskMethod);
    bankPositioningPanel.setVisible(!taskMethod.trim().isEmpty());
    KitPlan safeLoadout =
        loadout == null ? new KitPlan("—", "—", "No item scan available") : loadout;
    setOptionalWrappedText(
        recommendationDetails, buildRecommendationDetails(recommendation, safeLoadout));
    updateBankTagCard(safeLoadout);
    updateBankDartRecommendation(recommendation, safeLoadout);
    if (!routingEnabled) {
      setOptionalWrappedText(routeValue, "Routing disabled");
      actionButton.setText("Routing disabled");
      actionButton.setBackground(DISABLED_BG);
      actionButton.setEnabled(false);
    } else if (recommendation.hasDestination()) {
      setOptionalWrappedText(routeValue, "");
      actionButton.setText("Start route");
      actionButton.setBackground(BLUE);
      actionButton.setEnabled(true);
    } else {
      setOptionalWrappedText(routeValue, "Route target unavailable");
      actionButton.setText("Route unavailable");
      actionButton.setBackground(DISABLED_BG);
      actionButton.setEnabled(false);
    }
    refreshLayout();
  }

  public void showPositioningNote(String note) {
    String safeNote = compactPositioningNote(note);
    if (safeNote.isEmpty()) {
      recommendationPositioningPanel.setVisible(false);
      setOptionalWrappedText(recommendationPositioningValue, "");
    } else {
      setWrappedText(recommendationPositioningValue, safeNote);
      recommendationPositioningPanel.setVisible(true);
    }
    refreshLayout();
  }

  public void showPreparation(PreparationCatalog.PreparationPlan plan) {
    PreparationCatalog.PreparationPlan safePlan =
        plan == null ? PreparationCatalog.PreparationPlan.none() : plan;
    if (!safePlan.isActive()) {
      recommendationPreparationPanel.setVisible(false);
      bankPreparationPanel.setVisible(false);
      setOptionalWrappedText(recommendationPreparationValue, "");
      setOptionalWrappedText(recommendationPreparationNote, "");
      setOptionalWrappedText(bankPreparationValue, "");
      setOptionalWrappedText(bankPreparationNote, "");
      refreshLayout();
      return;
    }
    Color stateColor = safePlan.isReady() ? READY_GREEN : ACCENT;
    recommendationPreparationValue.setForeground(stateColor);
    bankPreparationValue.setForeground(stateColor);
    String compactHeadline = compactPreparationHeadline(safePlan.getHeadline());
    String actionableDetail =
        safePlan.isReady() ? "" : compactPreparationDetail(safePlan.getDetail());
    setOptionalWrappedText(recommendationPreparationValue, compactHeadline);
    setOptionalWrappedText(recommendationPreparationNote, actionableDetail);
    setOptionalWrappedText(bankPreparationValue, compactHeadline);
    setOptionalWrappedText(bankPreparationNote, actionableDetail);
    recommendationPreparationPanel.setVisible(true);
    bankPreparationPanel.setVisible(true);
    refreshLayout();
  }

  public void showTravelRecommendation(String itemName, String note) {
    String safeName = itemName == null ? "" : itemName.trim();
    String safeNote = cleanDetail(note);
    if (safeName.isEmpty() && safeNote.isEmpty()) {
      bankTravelRecommendationPanel.setVisible(false);
      setOptionalWrappedText(bankTravelRecommendationValue, "");
      setOptionalWrappedText(bankTravelRecommendationNote, "");
      refreshLayout();
      return;
    }
    if (safeName.isEmpty()) {
      setOptionalWrappedText(bankTravelRecommendationValue, safeNote);
      setOptionalWrappedText(bankTravelRecommendationNote, "");
      bankTravelRecommendationPanel.setVisible(true);
      refreshLayout();
      return;
    }
    setOptionalWrappedText(bankTravelRecommendationValue, playerFacingTravelAction(safeName));
    String authoredNote = playerFacingTravelNote(safeName);
    String normalizedName = safeName.toLowerCase(java.util.Locale.ENGLISH);
    boolean instructionSuppliesItsOwnNote =
        normalizedName.startsWith("follow ")
            || normalizedName.startsWith("walk ")
            || normalizedName.startsWith("enter ")
            || normalizedName.startsWith("climb ")
            || normalizedName.startsWith("open ");
    setOptionalWrappedText(
        bankTravelRecommendationNote,
        authoredNote.isEmpty() && instructionSuppliesItsOwnNote ? safeNote : authoredNote);
    bankTravelRecommendationPanel.setVisible(true);
    refreshLayout();
  }

  static String playerFacingTravelAction(String value) {
    String text = cleanDetail(value);
    String normalized = text.toLowerCase(java.util.Locale.ENGLISH);
    if (normalized.contains("light creature") && normalized.contains("chasm")) {
      return "Click the light creature → Into the chasm";
    }
    if (normalized.startsWith("climb ")
        || normalized.startsWith("enter ")
        || normalized.startsWith("follow ")
        || normalized.startsWith("walk ")
        || normalized.startsWith("open ")) {
      return text;
    }
    if (normalized.contains("ghommal")) {
      return "Withdraw and use " + text;
    }
    return text.isEmpty() ? "" : "Use " + text;
  }

  static String playerFacingTravelNote(String value) {
    String text = cleanDetail(value);
    String normalized = text.toLowerCase(java.util.Locale.ENGLISH);
    if (normalized.contains("ghommal")) {
      return "Teleport to Mor Ul Rek. Shortest Path starts after you land.";
    }
    if (normalized.contains("sapphire lantern")) {
      return "Use it on a light creature, descend, run south, climb both walls, then enter the"
                 + " skull.";
    }
    if (normalized.contains("light creature") && normalized.contains("chasm")) {
      return "After landing, run south, climb both walls, then enter the skull.";
    }
    return "";
  }

  public void showSessionState(boolean active, boolean available, String status) {
    sessionActive = active;
    sessionAvailable = available && sessionToggleAction != null;
    sessionButton.setText(active ? "Stop Slayer session" : "Start Slayer session");
    sessionButton.setBackground(active ? STOP_RED : sessionAvailable ? BLUE : DISABLED_BG);
    sessionButton.setEnabled(active || sessionAvailable);
    String visibleStatus = compactSessionStatus(active, available, status);
    sessionButton.setToolTipText(visibleStatus.isEmpty() ? null : visibleStatus);
    refreshLayout();
  }

  public void showBankTagStatus(String status) {
    String visibleStatus = playerFacingBankTagStatus(status);
    bankTagButton.setToolTipText(visibleStatus.isEmpty() ? null : visibleStatus);
    bankTagButton.setText("Create or update bank tag");
    bankTagButton.setBackground(bankTagReady ? BLUE : DISABLED_BG);
    bankTagButton.setEnabled(bankTagReady);
    refreshLayout();
  }

  public void showRouteStatus(String status) {
    setOptionalWrappedText(routeValue, status);
    refreshLayout();
  }

  public void showPointBoostStatus(String status) {
    setOptionalWrappedText(pointBoostStatus, status);
    refreshLayout();
  }

  public void showNoTask(int points, int streak, String master) {
    setWrappedText(taskName, "No active task");
    taskProgress.setText("");
    masterValue.setText(master);
    pointsValue.setText(Integer.toString(points));
    streakValue.setText(Integer.toString(streak));
    setWrappedText(assignedAreaValue, "—", COMPACT_VALUE_WRAP_WIDTH);
    assignedAreaRow.setVisible(false);
    setWrappedText(recommendationLocation, "Waiting for task");
    setWrappedText(recommendationMethod, "A recommendation will appear after a task is assigned");
    setOptionalWrappedText(recommendationDetails, "");
    setOptionalWrappedText(routeValue, "");
    configureTaskVariants(null, TaskVariant.STANDARD_TASK);
    updateBankTagCard(KitPlan.empty());
    updateBankDartRecommendation(null, KitPlan.empty());
    bankPositioningPanel.setVisible(false);
    setOptionalWrappedText(bankPositioningValue, "");
    showPositioningNote("");
    showPreparation(PreparationCatalog.PreparationPlan.none());
    actionButton.setText("Waiting for task");
    actionButton.setBackground(DISABLED_BG);
    actionButton.setEnabled(false);
    refreshLayout();
  }

  public void showLoggedOut() {
    showAccountName("NOT LOGGED IN");
    setPortrait(null);
    setWrappedText(taskName, "Not logged in");
    taskProgress.setText("");
    showPointBoostStatus("");
    masterValue.setText("—");
    pointsValue.setText("—");
    streakValue.setText("—");
    setWrappedText(assignedAreaValue, "—", COMPACT_VALUE_WRAP_WIDTH);
    assignedAreaRow.setVisible(false);
    setWrappedText(recommendationLocation, "Not available");
    setWrappedText(recommendationMethod, "Log in to generate a Slayer recommendation");
    setOptionalWrappedText(recommendationDetails, "");
    setOptionalWrappedText(routeValue, "");
    configureTaskVariants(null, TaskVariant.STANDARD_TASK);
    updateBankTagCard(KitPlan.empty());
    updateBankDartRecommendation(null, KitPlan.empty());
    bankPositioningPanel.setVisible(false);
    setOptionalWrappedText(bankPositioningValue, "");
    showPositioningNote("");
    showPreparation(PreparationCatalog.PreparationPlan.none());
    actionButton.setText("Waiting for login");
    actionButton.setBackground(DISABLED_BG);
    actionButton.setEnabled(false);
    showSessionState(false, false, "Log in to start a guided Slayer session");
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
}
