package org.isoron.uhabits.activities.games

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Lightweight offline puzzle arcade. All artwork is drawn with Canvas;
 * sound uses Android's built-in ToneGenerator (no audio assets or game engine).
 */
class PuzzleGamesActivity : AppCompatActivity() {
    private lateinit var board: GameBoard
    private lateinit var status: TextView
    private lateinit var soundButton: Button
    private var game = Game.MINES
    private var soundEnabled = true
    private val tone by lazy { ToneGenerator(AudioManager.STREAM_MUSIC, 55) }

    enum class Game(val label: String) {
        MINES("扫雷"), MERGE("2048"), SUDOKU("数独"), BOX("推箱子"), BIRD("飞鸟穿越")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        soundEnabled = getSharedPreferences("puzzle_games", MODE_PRIVATE).getBoolean("sound", true)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 247, 250))
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = "益智游戏"
            textSize = 21f
            setTextColor(Color.rgb(30, 45, 62))
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f))
        soundButton = Button(this).apply {
            text = if (soundEnabled) "声音：开" else "声音：关"
            textSize = 12f
            setOnClickListener {
                soundEnabled = !soundEnabled
                getSharedPreferences("puzzle_games", MODE_PRIVATE).edit().putBoolean("sound", soundEnabled).apply()
                text = if (soundEnabled) "声音：开" else "声音：关"
                playTone(ToneGenerator.TONE_PROP_ACK)
            }
        }
        header.addView(soundButton)
        root.addView(header)
        val choices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Game.values().forEach { item ->
            choices.addView(Button(this).apply {
                text = item.label
                textSize = 11f
                isAllCaps = false
                setPadding(dp(2), 0, dp(2), 0)
                setOnClickListener { selectGame(item) }
            }, LinearLayout.LayoutParams(0, dp(46), 1f))
        }
        root.addView(choices)
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(5), dp(6), dp(5))
        }
        root.addView(status)
        board = GameBoard(this)
        root.addView(board, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER }
        footer.addView(Button(this).apply {
            text = "重新开始"
            isAllCaps = false
            setOnClickListener { board.reset(); updateStatus() }
        })
        footer.addView(Button(this).apply {
            text = "游戏说明"
            isAllCaps = false
            setOnClickListener { showHelp() }
        })
        root.addView(footer)
        setContentView(root)
        selectGame(Game.MINES)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun selectGame(next: Game) {
        game = next
        board.game = next
        board.reset()
        updateStatus()
        board.invalidate()
    }

    private fun updateStatus() {
        status.text = when (game) {
            Game.MINES -> "扫雷 · 点格子翻开，长按插旗"
            Game.MERGE -> "2048 · 上下左右滑动，合并相同数字"
            Game.SUDOKU -> "数独 · 选中格子，再点下方数字填入"
            Game.BOX -> "推箱子 · 点方向键推动箱子到目标点"
            Game.BIRD -> "飞鸟穿越 · 点击屏幕起飞，避开绿色障碍"
        }
    }

    private fun playTone(toneType: Int) {
        if (soundEnabled) tone.startTone(toneType, 90)
    }

    private fun showHelp() {
        val message = when (game) {
            Game.MINES -> "找出所有地雷以外的格子。长按可以插旗；翻到地雷则结束。"
            Game.MERGE -> "滑动棋盘移动数字，相同数字碰到时合并。得到 2048 即可获胜。"
            Game.SUDOKU -> "每行、每列和每个 3×3 宫内，数字 1～9 不能重复。先做简单题。"
            Game.BOX -> "使用方向键推动箱子，把所有箱子推到圆点目标位置。箱子不能拉回。"
            Game.BIRD -> "点击屏幕让小鸟向上飞，松开后会下落。穿过水管之间的空隙得分。"
        }
        AlertDialog.Builder(this).setTitle(game.label).setMessage(message).setPositiveButton("知道了", null).show()
    }

    override fun onDestroy() {
        board.removeCallbacks(board.frameTick)
        tone.release()
        super.onDestroy()
    }

    inner class GameBoard(context: Context) : View(context) {
        var game = Game.MINES
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bg = Color.rgb(238, 243, 248)
        private var downX = 0f
        private var downY = 0f
        private var selectedRow = -1
        private var selectedCol = -1

        // Minesweeper
        private val mineN = 10
        private var mines = Array(9) { BooleanArray(9) }
        private var opened = Array(9) { BooleanArray(9) }
        private var flags = Array(9) { BooleanArray(9) }
        private var mineOver = false
        private var longPressTriggered = false
        private val longPressAction = Runnable {
            if (game == Game.MINES && !mineOver) {
                val cell = min(width / 9f, height / 9f)
                val left = (width - cell * 9) / 2f
                val top = (height - cell * 9) / 2f
                val col = ((downX - left) / cell).toInt()
                val row = ((downY - top) / cell).toInt()
                if (row in 0..8 && col in 0..8 && !opened[row][col]) {
                    flags[row][col] = !flags[row][col]
                    longPressTriggered = true
                    playTone(ToneGenerator.TONE_PROP_ACK)
                    invalidate()
                }
            }
        }

        // 2048
        private var tiles = Array(4) { IntArray(4) }
        private var mergeOver = false

        // Sudoku (a fixed, valid starter puzzle)
        private val puzzle = arrayOf(
            intArrayOf(5,3,0,0,7,0,0,0,0), intArrayOf(6,0,0,1,9,5,0,0,0),
            intArrayOf(0,9,8,0,0,0,0,6,0), intArrayOf(8,0,0,0,6,0,0,0,3),
            intArrayOf(4,0,0,8,0,3,0,0,1), intArrayOf(7,0,0,0,2,0,0,0,6),
            intArrayOf(0,6,0,0,0,0,2,8,0), intArrayOf(0,0,0,4,1,9,0,0,5),
            intArrayOf(0,0,0,0,8,0,0,7,9)
        )
        private var sudoku = Array(9) { r -> puzzle[r].clone() }

        // Sokoban
        private val level = arrayOf("########", "#  .   #", "#  $   #", "#  $ . #", "#   @  #", "# .    #", "########")
        private var playerX = 4
        private var playerY = 4
        private var boxes = mutableSetOf("3,2", "3,3")
        private val targets = setOf("3,1", "5,3")
        private var moves = 0

        // Bird game
        private data class Pipe(var x: Float, val gapY: Float, var scored: Boolean = false)
        private var birdY = 0.42f
        private var birdVelocity = 0f
        private var pipes = mutableListOf<Pipe>()
        private var birdScore = 0
        private var birdDead = false
        private var lastFrame = 0L
        private val frameTick = object : Runnable {
            override fun run() {
                if (game == Game.BIRD && !birdDead) {
                    val now = System.currentTimeMillis()
                    val dt = if (lastFrame == 0L) 0.016f else min(0.04f, (now - lastFrame) / 1000f)
                    lastFrame = now
                    updateBird(dt)
                    invalidate()
                    postDelayed(this, 16)
                }
            }
        }

        init { isFocusable = true; setLayerType(View.LAYER_TYPE_SOFTWARE, null) }

        fun reset() {
            mineOver = false
            mines = Array(9) { BooleanArray(9) }
            opened = Array(9) { BooleanArray(9) }
            flags = Array(9) { BooleanArray(9) }
            val spots = mutableSetOf<Int>()
            while (spots.size < mineN) spots.add(Random.nextInt(81))
            spots.forEach { mines[it / 9][it % 9] = true }
            tiles = Array(4) { IntArray(4) }
            mergeOver = false
            addTile(); addTile()
            sudoku = Array(9) { r -> puzzle[r].clone() }
            selectedRow = -1; selectedCol = -1
            playerX = 4; playerY = 4; boxes = mutableSetOf("3,2", "3,3"); moves = 0
            birdY = 0.42f; birdVelocity = 0f; pipes.clear(); birdScore = 0; birdDead = false
            lastFrame = 0L
            if (game == Game.BIRD) postDelayed(frameTick, 16)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(bg)
            when (game) {
                Game.MINES -> drawMines(canvas)
                Game.MERGE -> draw2048(canvas)
                Game.SUDOKU -> drawSudoku(canvas)
                Game.BOX -> drawBox(canvas)
                Game.BIRD -> drawBird(canvas)
            }
        }

        private fun paint(color: Int, size: Float = 1f, style: Paint.Style = Paint.Style.FILL): Paint {
            p.color = color; p.textSize = size; p.style = style; p.strokeWidth = max(1f, size / 12f); p.textAlign = Paint.Align.CENTER
            return p
        }

        private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int = Color.DKGRAY) {
            c.drawText(s, x, y, paint(color, size))
        }

        private fun drawMines(c: Canvas) {
            val cell = min(width / 9f, height / 9f)
            val left = (width - cell * 9) / 2f
            val top = (height - cell * 9) / 2f
            for (r in 0..8) for (col in 0..8) {
                val x = left + col * cell; val y = top + r * cell
                val rect = RectF(x + 2, y + 2, x + cell - 2, y + cell - 2)
                val openedCell = opened[r][col]
                c.drawRoundRect(rect, 5f, 5f, paint(if (openedCell) Color.rgb(220, 228, 235) else Color.rgb(112, 145, 166)))
                if (openedCell) {
                    if (mines[r][col]) text(c, "✹", x + cell/2, y + cell*.7f, cell*.58f, Color.RED)
                    else {
                        val n = neighbors(r, col)
                        if (n > 0) text(c, n.toString(), x + cell/2, y + cell*.7f, cell*.58f, Color.rgb(35, 85, 150))
                    }
                } else if (flags[r][col]) text(c, "⚑", x + cell/2, y + cell*.7f, cell*.55f, Color.YELLOW)
            }
            if (mineOver) text(c, "游戏结束 · 点击重新开始", width/2f, top + cell*9 + 30f, 18f, Color.RED)
        }

        private fun neighbors(r: Int, col: Int): Int {
            var n = 0
            for (y in max(0,r-1)..min(8,r+1)) for (x in max(0,col-1)..min(8,col+1)) if (mines[y][x]) n++
            return n
        }

        private fun reveal(r: Int, col: Int) {
            if (r !in 0..8 || col !in 0..8 || opened[r][col] || flags[r][col] || mineOver) return
            // Guarantee a safe first move, as in the classic rules.
            if (mines[r][col] && opened.all { row -> row.none { it } }) {
                mines[r][col] = false
                var replacement: Int
                do { replacement = Random.nextInt(81) } while (mines[replacement / 9][replacement % 9] || (replacement / 9 == r && replacement % 9 == col))
                mines[replacement / 9][replacement % 9] = true
            }
            opened[r][col] = true
            if (mines[r][col]) { mineOver = true; playTone(ToneGenerator.TONE_PROP_NACK); return }
            playTone(ToneGenerator.TONE_PROP_BEEP)
            if (neighbors(r,col) == 0) for (y in max(0,r-1)..min(8,r+1)) for (x in max(0,col-1)..min(8,col+1)) reveal(y,x)
            if (opened.sumOf { row -> row.count { it } } == 71) playTone(ToneGenerator.TONE_PROP_ACK)
        }

        private fun draw2048(c: Canvas) {
            val size = min(width - 24f, height - 24f)
            val cell = size / 4f
            val left = (width - size)/2f; val top = (height - size)/2f
            c.drawRoundRect(RectF(left,top,left+size,top+size),14f,14f,paint(Color.rgb(174,157,137)))
            val colors = mapOf(0 to 0xFFCDC1B4.toInt(), 2 to 0xFFEEE4DA.toInt(), 4 to 0xFFEDE0C8.toInt(), 8 to 0xFFF2B179.toInt(), 16 to 0xFFF59563.toInt(), 32 to 0xFFF67C5F.toInt(), 64 to 0xFFF65E3B.toInt(), 128 to 0xFFEDCF72.toInt(), 256 to 0xFFEDCC61.toInt(), 512 to 0xFFEDC850.toInt(), 1024 to 0xFFEDC53F.toInt(), 2048 to 0xFFEDC22E.toInt())
            for (r in 0..3) for (col in 0..3) {
                val v = tiles[r][col]; val x = left + col*cell; val y = top+r*cell
                c.drawRoundRect(RectF(x+4,y+4,x+cell-4,y+cell-4),8f,8f,paint(colors[v] ?: Color.DKGRAY))
                if (v > 0) text(c,v.toString(),x+cell/2,y+cell*.62f,if(v>=1024) cell*.25f else cell*.34f,if(v<=4) Color.DKGRAY else Color.WHITE)
            }
            if (mergeOver) text(c,"2048 · 再来一局？",width/2f,top+size+28,18f)
        }

        private fun addTile() {
            val empty = mutableListOf<Pair<Int,Int>>()
            for (r in 0..3) for (col in 0..3) if (tiles[r][col] == 0) empty.add(r to col)
            if (empty.isNotEmpty()) { val pos = empty.random(); tiles[pos.first][pos.second] = if(Random.nextInt(10)==0) 4 else 2 }
        }

        private fun move2048(dir: Int) {
            if (mergeOver) return
            var changed = false
            for (line in 0..3) {
                val values = (0..3).map { i -> when(dir) { 0 -> tiles[line][i]; 1 -> tiles[line][3-i]; 2 -> tiles[i][line]; else -> tiles[3-i][line] } }.filter { it != 0 }.toMutableList()
                val merged = mutableListOf<Int>(); var i = 0
                while(i < values.size) {
                    if(i+1 < values.size && values[i] == values[i+1]) { merged.add(values[i]*2); i+=2; playTone(ToneGenerator.TONE_PROP_ACK) }
                    else { merged.add(values[i]); i++ }
                }
                while(merged.size < 4) merged.add(0)
                for(j in 0..3) {
                    val r = when(dir) { 0,1 -> line; else -> if(dir==2) j else 3-j }
                    val col = when(dir) { 0 -> j; 1 -> 3-j; else -> line }
                    if(tiles[r][col] != merged[j]) changed = true
                    tiles[r][col] = merged[j]
                }
            }
            if(changed) addTile()
            if(tiles.any { row -> row.any { it == 2048 } }) { mergeOver = true; playTone(ToneGenerator.TONE_PROP_ACK) }
            invalidate()
        }

        private fun drawSudoku(c: Canvas) {
            val size = min(width - 20f, height - 150f)
            val cell = size / 9f
            val left = (width-size)/2f; val top = 12f
            c.drawRect(left,top,left+size,top+size,paint(Color.WHITE))
            for(r in 0..9) {
                val thick = if(r%3==0) 3f else 1f
                c.drawLine(left,top+r*cell,left+size,top+r*cell,paint(Color.DKGRAY,thick,Paint.Style.STROKE))
                c.drawLine(left+r*cell,top,left+r*cell,top+size,paint(Color.DKGRAY,thick,Paint.Style.STROKE))
            }
            for(r in 0..8) for(col in 0..8) {
                if(r==selectedRow && col==selectedCol) c.drawRect(left+col*cell,top+r*cell,left+(col+1)*cell,top+(r+1)*cell,paint(0x553F8FE8))
                val v=sudoku[r][col]
                if(v>0) text(c,v.toString(),left+(col+.5f)*cell,top+(r+.68f)*cell,cell*.52f,if(puzzle[r][col]>0) Color.DKGRAY else Color.rgb(35,105,190))
            }
            val keyY=top+size+35f
            for(n in 1..9) {
                val x=width*(n-.5f)/9f
                c.drawRoundRect(RectF(x-width/18f,keyY-22,x+width/18f,keyY+22),6f,6f,paint(Color.rgb(219,230,240)))
                text(c,n.toString(),x,keyY+8,20f,Color.rgb(30,90,150))
            }
        }

        private fun drawBox(c: Canvas) {
            val cell=min(width/8f,(height-24f)/7f)
            val left=(width-cell*8)/2f; val top=(height-cell*7)/2f
            for(y in 0..6) for(x in 0..7) {
                val ch=level[y][x]
                val px=left+x*cell; val py=top+y*cell
                val wall=ch=='#'
                c.drawRect(px+1,py+1,px+cell-1,py+cell-1,paint(if(wall) Color.rgb(80,100,118) else Color.rgb(225,232,226)))
                if(targets.contains("$x,$y")) c.drawCircle(px+cell/2,py+cell/2,cell*.17f,paint(Color.rgb(230,165,60)))
                if(boxes.contains("$x,$y")) c.drawRoundRect(RectF(px+cell*.16f,py+cell*.16f,px+cell*.84f,py+cell*.84f),5f,5f,paint(Color.rgb(174,112,62)))
                if(playerX==x && playerY==y) c.drawCircle(px+cell/2,py+cell/2,cell*.29f,paint(Color.rgb(55,130,210)))
            }
            val y=top+cell*7+30
            text(c,"步数：$moves    箱子到位：${boxes.count { it in targets }}/${targets.size}",width/2f,min(height-12,y),16f)
            val cy=height-90f
            drawArrow(c,width/2f,cy-34,"↑"); drawArrow(c,width/2f,cy+34,"↓"); drawArrow(c,width/2f-55,cy,"←"); drawArrow(c,width/2f+55,cy,"→")
        }

        private fun drawArrow(c: Canvas,x:Float,y:Float,label:String) {
            c.drawCircle(x,y,23f,paint(Color.rgb(75,130,175)))
            text(c,label,x,y+8,24f,Color.WHITE)
        }

        private fun moveBox(dx:Int,dy:Int) {
            val nx=playerX+dx; val ny=playerY+dy
            if(ny !in level.indices || nx !in 0 until 8 || level[ny][nx]=='#') return
            val key="$nx,$ny"
            if(key in boxes) {
                val bx=nx+dx; val by=ny+dy; val next="$bx,$by"
                if(by !in level.indices || bx !in 0 until 8 || level[by][bx]=='#' || next in boxes) return
                boxes.remove(key); boxes.add(next)
            }
            playerX=nx; playerY=ny; moves++; playTone(ToneGenerator.TONE_PROP_BEEP); invalidate()
            if(boxes.count { it in targets }==targets.size) playTone(ToneGenerator.TONE_PROP_ACK)
        }

        private fun drawBird(c: Canvas) {
            c.drawColor(Color.rgb(91,190,245))
            // Original scenery, drawn procedurally (no copied game artwork).
            for(i in 0..3) {
                val x=(i*width/3f+width*.12f)% (width+100f)
                c.drawCircle(x, height*.22f+(i%2)*28f, 20f, paint(Color.WHITE))
                c.drawCircle(x+18,height*.22f+(i%2)*28f,15f,paint(Color.WHITE))
            }
            // Distant city and rounded green tree line, all procedurally drawn.
            val skyline = intArrayOf(52, 76, 60, 92, 66, 82, 55)
            skyline.forEachIndexed { i, h ->
                val x = i * width / skyline.size.toFloat()
                val top = height * .78f - h
                c.drawRect(x, top, x + width / skyline.size.toFloat() + 2, height * .88f, paint(Color.rgb(100, 177, 211)))
                for (row in 0..2) for (col in 0..1) {
                    c.drawRect(x + 8 + col * 15, top + 12 + row * 17, x + 15 + col * 15, top + 21 + row * 17, paint(Color.rgb(191, 226, 226)))
                }
            }
            for(i in 0..9) {
                val x=i*width/9f
                c.drawCircle(x,height*.86f,26f,paint(if(i%2==0) Color.rgb(75,190,104) else Color.rgb(63,174,96)))
            }
            c.drawRect(0f,height*.88f,width.toFloat(),height.toFloat(),paint(Color.rgb(91,190,100)))
            c.drawRect(0f,height*.94f,width.toFloat(),height.toFloat(),paint(Color.rgb(183,133,79)))
            val pipeW=width*.15f; val gap=height*.25f
            pipes.forEach { pipe ->
                val x=pipe.x
                val topH=pipe.gapY-gap/2
                val bottomY=pipe.gapY+gap/2
                c.drawRect(x,0f,x+pipeW,topH,paint(Color.rgb(69,177,54)))
                c.drawRect(x-5,topH-18,x+pipeW+5,topH,paint(Color.rgb(92,205,65)))
                c.drawRect(x,bottomY,x+pipeW,height*.88f,paint(Color.rgb(69,177,54)))
                c.drawRect(x-5,bottomY,x+pipeW+5,bottomY+18,paint(Color.rgb(92,205,65)))
            }
            val bx=width*.28f; val by=birdY*height
            c.drawOval(RectF(bx-19,by-14,bx+20,by+14),paint(Color.rgb(255,210,45)))
            c.drawCircle(bx+10,by-5,4f,paint(Color.WHITE)); c.drawCircle(bx+12,by-5,2f,paint(Color.BLACK))
            c.drawTriangle(bx+17,by+2,bx+31,by+7,bx+17,by+10,Color.rgb(245,125,35))
            text(c,"得分：$birdScore",width/2f,42f,25f,Color.WHITE)
            if(birdDead) text(c,"游戏结束 · 点击重新开始",width/2f,height*.52f,21f,Color.WHITE)
            else text(c,"点击屏幕起飞",width/2f,height*.76f,16f,Color.WHITE)
        }

        private fun Canvas.drawTriangle(x1:Float,y1:Float,x2:Float,y2:Float,x3:Float,y3:Float,color:Int) {
            val path=android.graphics.Path().apply { moveTo(x1,y1); lineTo(x2,y2); lineTo(x3,y3); close() }
            drawPath(path,paint(color))
        }

        private fun updateBird(dt:Float) {
            birdVelocity += 1.35f*dt
            birdY += birdVelocity*dt
            val pw=width*.15f; val gap=height*.25f
            if(pipes.isEmpty() || pipes.last().x < width*.58f) pipes.add(Pipe(width+20f,Random.nextFloat()*.34f*height+height*.34f))
            pipes.forEach { pipe ->
                pipe.x -= width*.42f*dt
                val bx=width*.28f
                if(!pipe.scored && pipe.x+pw<bx) { pipe.scored=true; birdScore++; playTone(ToneGenerator.TONE_PROP_ACK) }
                val inX=bx+18>pipe.x && bx-18<pipe.x+pw
                if(inX && (birdY*height<pipe.gapY-gap/2 || birdY*height>pipe.gapY+gap/2)) birdDead=true
            }
            pipes.removeAll { it.x < -pw }
            if(birdY<.02f || birdY>.86f) birdDead=true
            if(birdDead) playTone(ToneGenerator.TONE_PROP_NACK)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when(event.action) {
                MotionEvent.ACTION_DOWN -> { downX=event.x; downY=event.y; longPressTriggered=false; if(game==Game.MINES) postDelayed(longPressAction, 500); return true }
                MotionEvent.ACTION_UP -> {
                    removeCallbacks(longPressAction)
                    val dx=event.x-downX; val dy=event.y-downY
                    when(game) {
                        Game.MINES -> {
                            if(longPressTriggered) return true
                            val cell=min(width/9f,height/9f); val left=(width-cell*9)/2f; val top=(height-cell*9)/2f
                            val col=((event.x-left)/cell).toInt(); val row=((event.y-top)/cell).toInt()
                            if(row in 0..8 && col in 0..8) reveal(row,col)
                        }
                        Game.MERGE -> if(max(abs(dx),abs(dy))>24) move2048(if(abs(dx)>abs(dy)) if(dx>0) 1 else 0 else if(dy>0) 3 else 2)
                        Game.SUDOKU -> {
                            val size=min(width-20f,height-150f); val cell=size/9f; val left=(width-size)/2f
                            if(event.y<12+size) {
                                val col=((event.x-left)/cell).toInt(); val row=((event.y-12)/cell).toInt()
                                if(row in 0..8 && col in 0..8 && puzzle[row][col]==0) { selectedRow=row; selectedCol=col }
                            } else if(selectedRow>=0) {
                                val n=((event.x/width)*9).toInt()+1
                                if(n in 1..9) { sudoku[selectedRow][selectedCol]=n; playTone(ToneGenerator.TONE_PROP_BEEP) }
                            }
                        }
                        Game.BOX -> {
                            val cy=height-90f
                            if(abs(event.x-width/2f)<30 && event.y<cy-10) moveBox(0,-1)
                            else if(abs(event.x-width/2f)<30 && event.y>cy+10) moveBox(0,1)
                            else if(event.x<width/2f) moveBox(-1,0) else moveBox(1,0)
                        }
                        Game.BIRD -> {
                            if(birdDead) reset() else { birdVelocity=-.62f; playTone(ToneGenerator.TONE_PROP_BEEP); if(lastFrame==0L) post(frameTick) }
                        }
                    }
                    invalidate(); return true
                }
                MotionEvent.ACTION_CANCEL -> removeCallbacks(longPressAction)
            }
            return true
        }

        override fun performClick(): Boolean { super.performClick(); return true }
    }
}
