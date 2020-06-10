package io.github.pulverizer.movecraft.commands;

import io.github.pulverizer.movecraft.Movecraft;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.text.Text;

public class ContactsCommand implements CommandExecutor {
    @Override
    public CommandResult execute(CommandSource src, CommandContext args) throws CommandException {
        // Contacts:
        //          [CraftType] [Size] [Name] [Commanded/Piloted By] [Range] <Compass Heading || Center Block Position>
        //     FULL: Airship (8,560) MSV Grasshopper commanded by BernardisGood - 3,500m - North-West - (250, 178, -3000)
        // FAR PING: UNKNOWN CONTACT (600) - 3750m - North-West
        return CommandResult.success();
    }

    public static void register() {
    }
}
