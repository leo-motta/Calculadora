package com.example.calculadorasimplesteste

import android.os.Bundle
import android.os.Handler
import android.widget.HorizontalScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.example.calculadorasimplesteste.databinding.ActivityMainBinding
import timber.log.Timber


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val sb = StringBuilder()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Timber.i("INICIO...")

        //Declaração de variáveis
        val visor = Visor("0")
        var em: String
        var lastChar: Char

        //Começo
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        binding.visor = visor


        binding.button0.setOnClickListener {
            mostrar("0")
        }

        binding.button1.setOnClickListener {
            mostrar("1")
        }

        binding.button2.setOnClickListener {
            mostrar("2")
        }

        binding.button3.setOnClickListener {
            mostrar("3")
        }

        binding.button4.setOnClickListener {
            mostrar("4")
        }

        binding.button5.setOnClickListener {
            mostrar("5")
        }

        binding.button6.setOnClickListener {
            mostrar("6")
        }

        binding.button7.setOnClickListener {
            mostrar("7")
        }

        binding.button8.setOnClickListener {
            mostrar("8")
        }

        binding.button9.setOnClickListener {
            mostrar("9")
        }


        binding.buttonMais.setOnClickListener {
            if(!checarErros()){
                mostrar("+")
            }
        }

        binding.buttonMenos.setOnClickListener {
            if(!checarErros()) {
                mostrar("-")
            }
        }

        binding.buttonVezes.setOnClickListener {
            if(!checarErros()) {
                mostrar("x")
            }
        }

        binding.buttonDivisao.setOnClickListener {
            if(!checarErros()) {
                mostrar("÷")
            }
        }

        binding.buttonResto.setOnClickListener {
            if(!checarErros()) {
                mostrar("%")
            }
        }

        binding.buttonVirgula.setOnClickListener{
            if(!checarErros()) {
                mostrar(",")
            }
        }

        binding.buttonAc.setOnClickListener {
            limpar()
        }

        binding.buttonIgual.setOnClickListener {
            em = sb.toString()
            em = em.replace('÷', '/')
            em = em.replace('x', '*')
            em = em.replace(',', '.')

            //Checa se o ultimo caracter é um número
            lastChar = em[em.length-1]
            if(lastChar in '0'..'9') {
                operar(em)
            } else{
                erro()
            }
        }
    }

    //Retorna true se a expressão contém erros
    // Corrige erros do tipo: dois operadores seguidos
    private fun checarErros(): Boolean{
        val ex: String = sb.toString()
        val charFinal: Char

        //Verifica se a string está vazia, se não estiver pega o caractere final
        if(ex.isEmpty()){
            return true
        } else {
            charFinal = ex[ex.length-1]
        }
        //retorna false se o caractere não for um número
        return (charFinal !in '0'..'9')
    }

    private fun operar(entrada: String){
        var resultString = Expressions().eval(entrada).toString()
        resultString = resultString.replace('.', ',')

        sb.clear()
        sb.append(resultString)
        binding.apply {
            invalidateAll()
            visor?.resultado = resultString
        }
    }

    private fun mostrar(simbolo: String) {
        sb.append(simbolo)
        binding.apply {
            invalidateAll()
            visor?.resultado = sb.toString()
        }
        rolarDireita()
    }

    private fun limpar(){

        sb.clear()
        binding.apply {
            invalidateAll()
            visor?.resultado = "0"
        }
    }

    private fun erro(){
        Toast.makeText(this, "Operação Inválida!", Toast.LENGTH_SHORT).show()
    }

    private fun rolarDireita(){
        Handler().postDelayed({
            binding.horizontalScroll.scrollTo(binding.tela.right, binding.tela.top)
        }, 100)

    }
}

