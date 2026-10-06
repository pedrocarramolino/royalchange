import type { GameType } from '@/domain/economy';

/** Reglas de cada mesa: secciones breves (título y párrafos) para quien no conoce el juego. */
export const GAME_RULES: Record<GameType, { title: string; sections: [string, string[]][] }> = {
  Blackjack: {
    title: 'Cómo se juega al blackjack',
    sections: [
      ['Objetivo', ['Sumar más que el crupier sin pasarte de 21. Las figuras valen 10 y el as, 1 u 11.']],
      [
        'Tu turno',
        [
          'Pedir: otra carta. Plantarse: te quedas con lo que tienes.',
          'Doblar: duplicas la apuesta, recibes una sola carta más y te plantas.',
          'Separar: con dos cartas del mismo valor, haces dos manos con una apuesta cada una (hasta cuatro manos).',
        ],
      ],
      ['El crupier', ['Pide carta hasta llegar a 17 o más y entonces se planta. Si se pasa de 21, ganas.']],
      ['Pagos', ['Blackjack (as y figura de entrada): 3 a 2. Ganar: 1 a 1. Empate: recuperas la apuesta.']],
    ],
  },
  Roulette: {
    title: 'Cómo se juega a la ruleta',
    sections: [
      ['Objetivo', ['Acertar dónde cae la bola: ruleta europea con los números del 0 al 36.']],
      [
        'Apuestas',
        [
          'Toca un número, la línea entre dos (caballo) o una esquina (cuatro números). Abajo y a los lados, las apuestas sencillas: docenas, columnas, rojo o negro, par o impar, 1–18 o 19–36.',
          'Arrastra el dedo por el tapete para poner fichas en varios números seguidos.',
        ],
      ],
      ['Pagos', ['Pleno 35 a 1 · caballo 17 a 1 · calle 11 a 1 · esquina 8 a 1 · seisena 5 a 1 · docena y columna 2 a 1 · rojo, negro, par, impar, mitades 1 a 1.', 'Si sale el 0, solo cobran las apuestas que lo incluyen.']],
    ],
  },
  Slots: {
    title: 'Cómo se juega a las slots',
    sections: [
      ['Objetivo', ['Alinear símbolos iguales de izquierda a derecha en alguna de las 10 líneas de premio.']],
      ['La apuesta', ['Eliges cuánto apuestas por línea; la tirada juega las 10 líneas a la vez.']],
      ['Premios', ['Cuentan 2 o más símbolos seguidos desde el primer rodillo (la cereza paga desde 2; el resto, desde 3). La corona es comodín: sustituye a cualquier símbolo. La tabla completa está en «Pagos».']],
    ],
  },
  Poker: {
    title: 'Cómo se juega al póker (Texas Hold’em)',
    sections: [
      ['Objetivo', ['Ganar el bote con la mejor jugada de cinco cartas, o haciendo que los demás se retiren.']],
      [
        'La mano',
        [
          'Recibes dos cartas propias. En la mesa salen cinco comunes: tres (flop), una (turn) y otra (river), con una ronda de apuestas antes de cada una y al final.',
          'En cada turno puedes pasar o igualar, subir la apuesta o retirarte.',
        ],
      ],
      ['Jugadas, de mejor a peor', ['Escalera real, escalera de color, póker, full, color, escalera, trío, doble pareja, pareja y carta alta.']],
      ['Fichas', ['Te sientas con las fichas que eliges; lo que ganas o pierdes en la mesa vuelve a tu saldo al levantarte.']],
    ],
  },
  Dice: {
    title: 'Cómo se juega a los dados',
    sections: [
      ['Objetivo', ['Acertar la suma de dos dados.']],
      [
        'Apuestas y pagos (apuesta incluida)',
        [
          'Menor (2 a 6) o mayor (8 a 12): ×2,3.',
          'Siete: ×5,8. Dobles (los dos dados iguales): ×5,8.',
          'Suma exacta: 2 o 12 ×35 · 3 o 11 ×17,5 · 4 o 10 ×11,5 · 5 o 9 ×8,7 · 6 u 8 ×6,9.',
        ],
      ],
    ],
  },
  Baccarat: {
    title: 'Cómo se juega al baccarat',
    sections: [
      ['Objetivo', ['Apostar a la mano que más se acerque a 9: la del jugador o la de la banca. También puedes apostar al empate o a que salga pareja.']],
      ['Valores', ['Del as al 9, su número; el 10 y las figuras, 0. Solo cuenta la última cifra: 7 + 8 = 15 vale 5.']],
      ['Reparto', ['Dos cartas para cada lado. Con 8 o 9 de salida (natural) se acaba. Si no, una tercera carta según reglas fijas: tú no tienes que decidir nada.']],
      ['Pagos', ['Jugador 1 a 1 · banca 1 a 1 (si gana con 6, 1 a 2) · empate 8 a 1 · pareja del jugador o de la banca 11 a 1.']],
    ],
  },
  VideoPoker: {
    title: 'Cómo se juega al video póker',
    sections: [
      ['Objetivo', ['Conseguir la mejor jugada de póker con cinco cartas (Jacks or Better).']],
      ['La mano', ['Recibes cinco cartas. Toca las que quieras guardar y pulsa Cambiar: las demás se sustituyen una sola vez.']],
      ['Pagos', ['Desde una pareja de jotas o mejor. La tabla de la izquierda muestra lo que paga cada jugada con tu apuesta; la escalera real paga 800 veces.']],
    ],
  },
  Plinko: {
    title: 'Cómo se juega al plinko',
    sections: [
      ['Objetivo', ['Soltar la bola y ver en qué casilla cae: cada una multiplica tu apuesta.']],
      ['Riesgo', ['Bajo: premios pequeños pero frecuentes. Alto: los extremos pagan mucho más, y el centro, casi nada.']],
      ['Consejo', ['Puedes tener varias bolas cayendo a la vez, o usar el juego automático para soltar varias seguidas.']],
    ],
  },
  Scratch: {
    title: 'Cómo se juega al rasca y gana',
    sections: [
      ['Objetivo', ['Rasca las nueve casillas del boleto: si salen tres símbolos iguales, ganas su premio.']],
      ['Premios (tres iguales)', ['Corona ×100 · 7 ×50 · BAR ×20 · diamante ×10 · pica ×5 · corazón ×2 · cereza ×1 (recuperas el boleto).']],
      ['Consejo', ['Rasca con el dedo; «Rascar todo» destapa de golpe lo que quede. Uno de cada tres boletos tiene premio.']],
    ],
  },
};
