package io.github.pulverizer.movecraft.sign;

import io.github.pulverizer.movecraft.Movecraft;
import io.github.pulverizer.movecraft.config.Settings;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.data.value.mutable.ListValue;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;
import org.spongepowered.api.text.Text;

import java.sql.*;
import java.sql.Date;
import java.util.*;

/**
 * Permissions Checked
 * Code to be reviewed
 *
 * @author BernardisGood
 * @version 1.1 - 17 Apr 2020
 */
public final class CommanderSign {

    private static final String HEADER = "Commander:";
    private static final String MAIN_TABLE = "CommanderSigns_Main";
    private static final String MEMBER_TABLE = "CommanderSigns_Members";

    public static void onSignChange(ChangeSignEvent event, Player player) {

        if (Settings.RequireCreateSignPerm && !player.hasPermission("movecraft.commandersign")) {
            player.sendMessage(Text.of("Insufficient Permissions"));
            event.setCancelled(true);
            return;
        }

        Connection sqlConnection = Movecraft.getInstance().connectToSQL();

        if (sqlConnection == null) {
            fail(event, player);
            return;
        }

        try {

            PreparedStatement statement = sqlConnection.prepareStatement("SELECT ID FROM " + MAIN_TABLE + " WHERE Username = ?");
            statement.setString(1, player.getName());

            ResultSet resultSet = statement.executeQuery();

            List<Integer> usedNumIDs = new ArrayList<>();
            int availableID = -1;
            int lastCheckedID = -1;

            while (resultSet.next()) {

                usedNumIDs.add(resultSet.getInt("ID"));
            }

            if (usedNumIDs.size() == Integer.MAX_VALUE) {
                fail(event, player);
                return;
            }
            Collections.sort(usedNumIDs);

            for (int id : usedNumIDs) {

                if (id == lastCheckedID + 1) {
                    lastCheckedID = id;
                    continue;
                }

                if (id > lastCheckedID + 1) {
                    availableID = lastCheckedID + 1;
                    break;
                }
            }

            if (availableID == -1 && availableID < lastCheckedID || lastCheckedID == -1)
                availableID = lastCheckedID + 1;


            if (availableID == -1) {
                sqlConnection.close();
                fail(event, player);
                return;
            }


            statement = sqlConnection.prepareStatement("INSERT INTO " + MAIN_TABLE + " (Username, ID) VALUES (?, ?)");

            statement.setString(1, player.getName());
            statement.setInt(2, availableID);

            statement.execute();

            statement.close();

            statement = sqlConnection.prepareStatement("INSERT INTO " + MEMBER_TABLE + " (UUID, Username, ID, isOwner) VALUES (?, ?, ?, ?)");
            statement.setString(1, player.getUniqueId().toString());
            statement.setString(2, player.getName());
            statement.setInt(3, availableID);
            statement.setBoolean(4, true);

            statement.execute();

            statement.close();

            ListValue<Text> lines = event.getText().lines();

            lines.set(1, Text.of(player.getName()));
            lines.set(2, Text.of(availableID));

            event.getText().set(lines);

            sqlConnection.close();

            return;

        } catch (SQLException e) {
            e.printStackTrace();
        }

        player.sendMessage(Text.of("There was an error. Please try placing the sign again. If this continues, please contact a Server Admin."));
    }

    //TODO: TileEntity missing if attached surface is broken, otherwise is okay?
    public static void onSignBreak(ChangeBlockEvent.Break event, Transaction<BlockSnapshot> transaction) {

        BlockSnapshot blockSnapshot = transaction.getOriginal();

        if (blockSnapshot.getState().getType() != BlockTypes.STANDING_SIGN && blockSnapshot.getState().getType() != BlockTypes.WALL_SIGN)
            return;

        if (!blockSnapshot.getLocation().isPresent() || !blockSnapshot.getLocation().get().getTileEntity().isPresent())
            return;

        ListValue<Text> signText = ((Sign) blockSnapshot.getLocation().get().getTileEntity().get()).lines();

        String string = signText.get(0).toPlain();

        if (!string.equalsIgnoreCase(HEADER))
            return;

        String username = signText.get(1).toPlain();
        int id = Integer.parseInt(signText.get(2).toPlain());

        if (!removeFromDatabase(username, id))
            transaction.setValid(false);
    }

    private static boolean removeFromDatabase(String username, int id) {

        Connection sqlConnection = Movecraft.getInstance().connectToSQL();

        if (sqlConnection == null)
            return false;

        try {

            PreparedStatement statement = sqlConnection.prepareStatement("DELETE FROM " + MEMBER_TABLE + " WHERE Username = ? AND ID = ?");
            statement.setString(1, username);
            statement.setInt(2, id);

            statement.execute();
            statement.close();

            statement = sqlConnection.prepareStatement("DELETE FROM " + MAIN_TABLE + " WHERE Username = ? AND ID = ?");
            statement.setString(1, username);
            statement.setInt(2, id);

            statement.execute();
            statement.close();

            sqlConnection.close();

            return true;

        } catch (SQLException e) {
            e.printStackTrace();

            try {
                sqlConnection.close();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }

            return false;
        }
    }

    public static Map.Entry<Date, Time> getCreationTimeStamp(String username, int id) {

        Connection sqlConnection = Movecraft.getInstance().connectToSQL();

        if (sqlConnection == null)
            return null;

        try {

            PreparedStatement statement = sqlConnection.prepareStatement("SELECT DateCreated, TimeCreated FROM " + MAIN_TABLE + " WHERE Username = ? AND ID = ?");

            statement.setString(1, username);
            statement.setInt(2, id);

            ResultSet results = statement.executeQuery();

            HashMap<Date, Time> map = new HashMap<>();

            if (results.next())
                map.put(results.getDate("DateCreated"), results.getTime("TimeCreated"));


            statement.close();
            sqlConnection.close();

            return map.entrySet().iterator().next();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static HashMap<UUID, Boolean> getMembers(String username, int id) {

        Connection sqlConnection = Movecraft.getInstance().connectToSQL();

        if (sqlConnection == null)
            return null;


        try {

            PreparedStatement statement = sqlConnection.prepareStatement("SELECT UUID, isOwner FROM " + MEMBER_TABLE + " WHERE Username = ? AND ID = ?");

            statement.setString(1, username);
            statement.setInt(2, id);

            HashMap<UUID, Boolean> members = new HashMap<>();

            ResultSet results = statement.executeQuery();

            while (results.next())
                members.put(UUID.fromString(results.getString("UUID")), results.getBoolean("isOwner"));

            statement.close();
            sqlConnection.close();

            return members;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void fail(ChangeSignEvent event, Player player) {

        player.sendMessage(Text.of("Please contact a Server Admin! There is something seriously wrong!"));

        ListValue<Text> lines = event.getText().lines();

        lines.set(1, Text.of("ERROR:"));
        lines.set(2, Text.of("Invalid SQL"));

        event.getText().set(lines);
    }

    public static void initDatabase() {

        Connection sqlConnection = Movecraft.getInstance().connectToSQL();

        PreparedStatement statement;

        try {

            statement = sqlConnection.prepareStatement("CREATE TABLE IF NOT EXISTS " + MAIN_TABLE + " (" +
                    "Username CHAR(16) NOT NULL, " +
                    "ID INT(16) NOT NULL, " +
                    "DateCreated DATE DEFAULT CURRENT_DATE, " +
                    "TimeCreated TIME DEFAULT CURRENT_TIME," +
                    "CONSTRAINT " + MAIN_TABLE + "_PKey PRIMARY KEY (Username, ID)" +
                    ")");

            statement.execute();
            statement.close();


            statement = sqlConnection.prepareStatement("CREATE TABLE IF NOT EXISTS " + MEMBER_TABLE + " (" +
                    "UUID CHAR(36) NOT NULL, " +
                    "Username CHAR(16) NOT NULL, " +
                    "ID CHAR(16) NOT NULL," +
                    "isOwner BOOL NOT NULL DEFAULT FALSE," +
                    "CONSTRAINT " + MEMBER_TABLE + "_FKey FOREIGN KEY (Username, ID) REFERENCES " + MAIN_TABLE + "(Username, ID)," +
                    "CONSTRAINT " + MEMBER_TABLE + "_PKey PRIMARY KEY (UUID, Username, ID)" +
                    ")");

            statement.execute();
            statement.close();

            sqlConnection.close();

        } catch (SQLException e) {
            e.printStackTrace();
        }

    }
}