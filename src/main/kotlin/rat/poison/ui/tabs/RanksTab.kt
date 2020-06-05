package rat.poison.ui.tabs

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.kotcrab.vis.ui.widget.VisLabel
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.tabbedpane.Tab
import rat.poison.curLocalization
import rat.poison.scripts.*

class RanksTab : Tab(false, false) {
    private val table = VisTable(true)

    var ranksListTable = VisTable()

    var teamsLabel = VisLabel()
    var namesLabel = VisLabel()
    var ranksLabel = VisLabel()
    var killsLabel = VisLabel()
    var deathsLabel = VisLabel()
    var KDsLabel = VisLabel()
    var winsLabel = VisLabel()

    init {
        ranksListTable.add(teamsLabel)
        ranksListTable.add(namesLabel)
        ranksListTable.add(ranksLabel)
        ranksListTable.add(killsLabel)
        ranksListTable.add(deathsLabel)
        ranksListTable.add(KDsLabel)
        ranksListTable.add(winsLabel)

        table.add(ranksListTable).maxWidth(500F)
    }

    override fun getContentTable(): Table? {
        return table
    }

    override fun getTabTitle(): String? {
        return curLocalization["RANKS_TAB_NAME"]
    }

    fun updateRanks() {
        teamsLabel.setText(curLocalization["RANKS_TEAM"] + " \n")
        namesLabel.setText(curLocalization["RANKS_NAME"] + " \n")
        ranksLabel.setText(curLocalization["RANKS_RANK"] + " \n")
        killsLabel.setText(curLocalization["RANKS_KILLS"] + " \n")
        deathsLabel.setText(curLocalization["RANKS_DEATHS"] + " \n")
        KDsLabel.setText(curLocalization["RANKS_KD"] + " \n")
        winsLabel.setText(curLocalization["RANKS_WINS"] + " \n")

        for (i in 0 until teamList.size-1) {
            teamsLabel.setText(teamsLabel.text.toString() + teamList[i] + "  \n")
            namesLabel.setText(namesLabel.text.toString() + nameList[i] + "  \n")
            ranksLabel.setText(ranksLabel.text.toString() + rankList[i] + "  \n")
            killsLabel.setText(killsLabel.text.toString() + killsList[i] + "  \n")
            deathsLabel.setText(deathsLabel.text.toString() + deathsList[i] + "  \n")
            KDsLabel.setText(KDsLabel.text.toString() + KDList[i] + "  \n")
            winsLabel.setText(winsLabel.text.toString() + winsList[i] + "  \n")
        }
    }
}

//        ctPlayers.forEachIndexed { _, ent->
//            ent.forEachIndexed { idx, str->
//                sb.append(str)
//
//                if ((idx != ent.size-1)) {
//                    //for (i in 0 until 15 - str.length) {
//                        sb.append("          ")
//                    //}
//                }
//            }
//            sb.appendln()
//        }
//        ctList.setText(sb)
//        sb.clear()
//        tPlayers.forEachIndexed { _, ent->
//            ent.forEachIndexed { idx, str->
//                sb.append(str)
//
//                if ((idx != ent.size-1)) {
//                    //for (i in 0 until 15 - str.length) {
//                        sb.append("          ")
//                    //}
//                }
//            }
//            sb.appendln()
//        }