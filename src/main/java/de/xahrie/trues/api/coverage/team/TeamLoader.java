package de.xahrie.trues.api.coverage.team;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.xahrie.trues.api.coverage.player.PlayerLoader;
import de.xahrie.trues.api.coverage.player.PrimePlayerFactory;
import de.xahrie.trues.api.coverage.player.model.PRMPlayer;
import de.xahrie.trues.api.coverage.team.model.AbstractTeam;
import de.xahrie.trues.api.coverage.team.model.PRMTeam;
import de.xahrie.trues.api.coverage.GamesportsLoader;
import de.xahrie.trues.api.riot.api.RiotName;
import de.xahrie.trues.api.util.StringUtils;
import de.xahrie.trues.api.util.io.request.HTML;
import de.xahrie.trues.api.util.io.request.URLType;
import lombok.Getter;
import lombok.experimental.ExtensionMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Lade Team und dessen Teaminfos von primeleague.gg
 */
@Getter
@ExtensionMethod(StringUtils.class)
public class TeamLoader extends GamesportsLoader {
  protected static List<AbstractTeam> loadedTeams = new ArrayList<>();

  public static void reset() {
    loadedTeams.clear();
  }

  public static int idFromURL(@NotNull String url) {
    return Integer.parseInt(url.between("/teams/", "-"));
  }

  @Nullable
  public static TeamLoader create(int teamId) {
    final TeamLoader teamLoader = new TeamLoader(teamId);
    if (teamLoader.html == null || teamLoader.html.text() == null) return null;

    final String teamTitle = teamLoader.html.find("h1").text();
    final String name = teamTitle.before(" (", -1);
    final String abbreviation = teamTitle.between("(", ")", -1);
    teamLoader.team = TeamFactory.getTeam(teamLoader.id, name, abbreviation);
    return teamLoader;
  }

  private PRMTeam team;

  public TeamLoader(@NotNull PRMTeam team) {
    super(URLType.TEAM, team.getPrmId());
    this.team = team;
  }

  public TeamLoader(int teamId) {
    super(URLType.TEAM, teamId);
  }

  public TeamHandler load() {
    if (html == null || html.text() == null) return null;

    final String teamTitle = html.find("h1").text();
    if (teamTitle != null) {
      team.setName(teamTitle.before(" (", -1));
      team.setAbbreviation(teamTitle.between("(", ")", -1));
    } else System.err.println(getId());

    return TeamHandler.builder()
        .html(html)
        .url(url)
        .team(team)
        .players(getPlayers())
        .build();
  }

  public PRMPlayer getPlayer(int prmId) {
    List<String> teamInfos = html.find("div", HTML.TEAM_HEAD).findAll("li").stream()
        .map(HTML::text).map(str -> str.after(":")).toList();
    teamInfos = teamInfos.subList(3, teamInfos.size());
    if (teamInfos.size() == 4) return null;

    for (HTML user : html.find("ul", HTML.PLAYERS + "-l").findAll("li")) {
      final int primeId = PlayerLoader.idFromURL(user.find("a").getAttribute("href"));
      if (primeId == prmId) {
        final String name = user.find("div", HTML.DESCRIPTION).find("span").text();
        final PRMPlayer primePlayer = PrimePlayerFactory.getPrimePlayer(primeId, RiotName.of(name));
        if (primePlayer != null) primePlayer.setTeam(team);
        return primePlayer;
      }
    }
    return null;
  }

  private List<PRMPlayer> getPlayers() {
    List<String> teamInfos = html.find("div", HTML.TEAM_HEAD).findAll("li").stream()
        .map(HTML::text).map(str -> str.after(":")).toList();
    teamInfos = teamInfos.subList(3, teamInfos.size());
    if (teamInfos.size() == 4) return List.of();

    final var players = new ArrayList<PRMPlayer>();
    for (HTML user : html.find("ul", HTML.PLAYERS + "-l").findAll("li")) {
      final int primeId = PlayerLoader.idFromURL(user.find("a").getAttribute("href"));
      final String name = user.find("div", HTML.DESCRIPTION).find("span").text();
      final PRMPlayer player = PrimePlayerFactory.getPrimePlayer(primeId, RiotName.of(name));
      if (player != null) {
        player.setTeam(team);
        players.add(player);
      }
    }

    team.getPlayers().stream().filter(player -> !players.contains((PRMPlayer) player)).filter(Objects::nonNull)
        .forEach(player -> new PlayerLoader(((PRMPlayer) player).getPrmUserId(), player.getName()).handleLeftTeam());
    return players;
  }

  public TeamHistory getHistoryOf(String name) {
    final List<HTML> div = html.findAll("table");
    for (final HTML season : div) {
      final List<HTML> rows = season.findAll("tr");
      if (rows.size() < 3) continue;
      final String ref = rows.get(2).findAll("td").get(1).find("a").getAttribute("href");
      if (ref != null && ref.contains(name))
        return historyOfHTML(season);
    }
    return null;
  }

  private TeamHistory historyOfHTML(HTML season) {
    List<HTML> findAll = season.findAll("tr");
    String kaliResult = findAll.get(1).findAll("td").get(2).text();
    kaliResult = (kaliResult.equals("-") || kaliResult.equals("Disqualifiziert")) ?
        null : kaliResult.between("(", "/");

    String groupName = findAll.get(2).findAll("td").get(1).text();
    groupName = groupName.contains("Starter") ? "9" : groupName.equals("-") ?
        null : groupName.between("Gruppe ", ".");

    String groupResult = findAll.get(2).findAll("td").get(2).text();
    groupResult = (groupResult.equals("-") || groupResult.equals("Disqualifiziert")) ? null :
        (Objects.equals(groupName, "9") ? groupResult.between("(", "/") : groupResult.between("Rang: ", "."));

    String playoff = findAll.get(3).findAll("td").get(1).text().trim();
    playoff = playoff.equals("-") ? null : playoff.between("Playoffs  ", ".");

    String playoffResult = findAll.get(3).findAll("td").get(2).text();
    final String lastResult = playoffResult.substring(playoffResult.length() - 1);
    Boolean hasWon = playoff == null ? null : lastResult.equals(playoff);
    return new TeamHistory(kaliResult.intValue(), groupName.intValue(), groupResult.intValue(),
        playoff.intValue(), hasWon);
  }
}
