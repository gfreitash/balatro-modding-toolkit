import br.com.ghfreitas.bmt.projectmanagement.presentation.cli.Entrypoint
import br.com.ghfreitas.bmt.projectmanagement.presentation.cli.FindManifestsCommand
import br.com.ghfreitas.bmt.projectmanagement.presentation.cli.InitCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands

fun main(args: Array<String>) {
    Entrypoint().subcommands(
        InitCommand(),
        FindManifestsCommand()
    ).main(args)
}
