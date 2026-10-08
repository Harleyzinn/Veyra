// Generated from the existing Kotlin catalogs. IDs and field names remain compatible.
export const catalog = [
  {
    "type": "rule",
    "label": "Automações",
    "group": "Sistema",
    "fields": [
      {
        "key": "condition",
        "label": "Quando",
        "kind": "CHOICE",
        "choices": [
          "Tarefa atrasada",
          "Orçamento em 80%",
          "Produto vence em 7 dias",
          "Manutenção próxima"
        ],
        "required": false
      },
      {
        "key": "enabled",
        "label": "Ativa",
        "kind": "CHOICE",
        "choices": [
          "Sim",
          "Não"
        ],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "template",
    "label": "Templates",
    "group": "Sistema",
    "fields": [
      {
        "key": "lines",
        "label": "Tarefas • uma por linha",
        "kind": "TEXT",
        "choices": [],
        "required": true
      }
    ],
    "checkable": false
  },
  {
    "type": "expense",
    "label": "Despesas",
    "group": "Finanças",
    "fields": [
      {
        "key": "amount",
        "label": "Valor",
        "kind": "MONEY",
        "choices": [],
        "required": true
      },
      {
        "key": "category",
        "label": "Categoria",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "subcategory",
        "label": "Subcategoria",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "currency",
        "label": "Moeda ISO • BRL, USD, EUR",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "account",
        "label": "Conta",
        "kind": "REFERENCE",
        "choices": [],
        "required": false
      },
      {
        "key": "card",
        "label": "Cartão",
        "kind": "REFERENCE",
        "choices": [],
        "required": false
      },
      {
        "key": "planned",
        "label": "Previsto",
        "kind": "CHOICE",
        "choices": [
          "Não",
          "Sim"
        ],
        "required": false
      },
      {
        "key": "paymentMethod",
        "label": "Forma de pagamento",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "dueDate",
        "label": "Vencimento",
        "kind": "DATE",
        "choices": [],
        "required": false
      },
      {
        "key": "competence",
        "label": "Competência • AAAA-MM",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "person",
        "label": "Pessoa / empresa",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "costCenter",
        "label": "Centro de custo",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "income",
    "label": "Receitas",
    "group": "Finanças",
    "fields": [
      {
        "key": "amount",
        "label": "Valor",
        "kind": "MONEY",
        "choices": [],
        "required": true
      },
      {
        "key": "category",
        "label": "Categoria",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "subcategory",
        "label": "Subcategoria",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "currency",
        "label": "Moeda ISO • BRL, USD, EUR",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "account",
        "label": "Conta",
        "kind": "REFERENCE",
        "choices": [],
        "required": false
      },
      {
        "key": "card",
        "label": "Cartão",
        "kind": "REFERENCE",
        "choices": [],
        "required": false
      },
      {
        "key": "planned",
        "label": "Previsto",
        "kind": "CHOICE",
        "choices": [
          "Não",
          "Sim"
        ],
        "required": false
      },
      {
        "key": "paymentMethod",
        "label": "Forma de pagamento",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "dueDate",
        "label": "Vencimento",
        "kind": "DATE",
        "choices": [],
        "required": false
      },
      {
        "key": "competence",
        "label": "Competência • AAAA-MM",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "person",
        "label": "Pessoa / empresa",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "costCenter",
        "label": "Centro de custo",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "account",
    "label": "Contas e carteiras",
    "group": "Finanças",
    "fields": [
      {
        "key": "opening",
        "label": "Saldo inicial",
        "kind": "MONEY",
        "choices": [],
        "required": false
      },
      {
        "key": "bank",
        "label": "Banco / instituição",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "accountType",
        "label": "Tipo",
        "kind": "CHOICE",
        "choices": [
          "Corrente",
          "Digital",
          "Poupança",
          "Dinheiro",
          "Carteira",
          "Investimentos",
          "Outras"
        ],
        "required": false
      },
      {
        "key": "currency",
        "label": "Moeda ISO",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "color",
        "label": "Cor",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "transfer",
    "label": "Transferências",
    "group": "Finanças",
    "fields": [
      {
        "key": "amount",
        "label": "Valor",
        "kind": "MONEY",
        "choices": [],
        "required": true
      },
      {
        "key": "account",
        "label": "Origem",
        "kind": "REFERENCE",
        "choices": [],
        "required": true
      },
      {
        "key": "destination",
        "label": "Destino",
        "kind": "REFERENCE",
        "choices": [],
        "required": true
      }
    ],
    "checkable": false
  },
  {
    "type": "card",
    "label": "Cartões",
    "group": "Finanças",
    "fields": [
      {
        "key": "limit",
        "label": "Limite",
        "kind": "MONEY",
        "choices": [],
        "required": false
      },
      {
        "key": "closing",
        "label": "Dia de fechamento",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "due",
        "label": "Dia de vencimento",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "bank",
        "label": "Banco",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "brand",
        "label": "Bandeira",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "lastFour",
        "label": "Últimos 4 dígitos",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "currency",
        "label": "Moeda ISO",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "account",
        "label": "Conta para pagamento",
        "kind": "REFERENCE",
        "choices": [],
        "required": false
      },
      {
        "key": "color",
        "label": "Cor",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "budget",
    "label": "Orçamentos",
    "group": "Finanças",
    "fields": [
      {
        "key": "category",
        "label": "Categoria",
        "kind": "TEXT",
        "choices": [],
        "required": true
      },
      {
        "key": "amount",
        "label": "Limite mensal",
        "kind": "MONEY",
        "choices": [],
        "required": true
      }
    ],
    "checkable": false
  },
  {
    "type": "subscription",
    "label": "Assinaturas e recorrências",
    "group": "Finanças",
    "fields": [
      {
        "key": "amount",
        "label": "Valor",
        "kind": "MONEY",
        "choices": [],
        "required": true
      },
      {
        "key": "category",
        "label": "Categoria",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "subcategory",
        "label": "Subcategoria",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "currency",
        "label": "Moeda ISO • BRL, USD, EUR",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "account",
        "label": "Conta",
        "kind": "REFERENCE",
        "choices": [],
        "required": false
      },
      {
        "key": "card",
        "label": "Cartão",
        "kind": "REFERENCE",
        "choices": [],
        "required": false
      },
      {
        "key": "planned",
        "label": "Previsto",
        "kind": "CHOICE",
        "choices": [
          "Não",
          "Sim"
        ],
        "required": false
      },
      {
        "key": "paymentMethod",
        "label": "Forma de pagamento",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "dueDate",
        "label": "Vencimento",
        "kind": "DATE",
        "choices": [],
        "required": false
      },
      {
        "key": "competence",
        "label": "Competência • AAAA-MM",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "person",
        "label": "Pessoa / empresa",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "costCenter",
        "label": "Centro de custo",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "frequency",
        "label": "Frequência",
        "kind": "CHOICE",
        "choices": [],
        "required": false
      },
      {
        "key": "endDate",
        "label": "Fim da série",
        "kind": "DATE",
        "choices": [],
        "required": false
      },
      {
        "key": "occurrenceCount",
        "label": "Quantidade de ocorrências",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      }
    ],
    "checkable": true
  },
  {
    "type": "installment_plan",
    "label": "Compras parceladas",
    "group": "Finanças",
    "fields": [
      {
        "key": "amount",
        "label": "Total",
        "kind": "MONEY",
        "choices": [],
        "required": false
      },
      {
        "key": "count",
        "label": "Parcelas",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "investment",
    "label": "Investimentos manuais",
    "group": "Finanças",
    "fields": [
      {
        "key": "amount",
        "label": "Valor investido",
        "kind": "MONEY",
        "choices": [],
        "required": false
      },
      {
        "key": "current",
        "label": "Valor atual",
        "kind": "MONEY",
        "choices": [],
        "required": false
      },
      {
        "key": "institution",
        "label": "Instituição",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "currency",
        "label": "Moeda ISO",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "recurring_rule",
    "label": "Séries financeiras",
    "group": "Finanças",
    "fields": [
      {
        "key": "amount",
        "label": "Valor",
        "kind": "MONEY",
        "choices": [],
        "required": true
      },
      {
        "key": "category",
        "label": "Categoria",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "subcategory",
        "label": "Subcategoria",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "currency",
        "label": "Moeda ISO • BRL, USD, EUR",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "account",
        "label": "Conta",
        "kind": "REFERENCE",
        "choices": [],
        "required": false
      },
      {
        "key": "card",
        "label": "Cartão",
        "kind": "REFERENCE",
        "choices": [],
        "required": false
      },
      {
        "key": "planned",
        "label": "Previsto",
        "kind": "CHOICE",
        "choices": [
          "Não",
          "Sim"
        ],
        "required": false
      },
      {
        "key": "paymentMethod",
        "label": "Forma de pagamento",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "dueDate",
        "label": "Vencimento",
        "kind": "DATE",
        "choices": [],
        "required": false
      },
      {
        "key": "competence",
        "label": "Competência • AAAA-MM",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "person",
        "label": "Pessoa / empresa",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "costCenter",
        "label": "Centro de custo",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "transactionType",
        "label": "Tipo",
        "kind": "CHOICE",
        "choices": [
          "income",
          "expense"
        ],
        "required": false
      },
      {
        "key": "frequency",
        "label": "Frequência",
        "kind": "CHOICE",
        "choices": [],
        "required": false
      },
      {
        "key": "startDate",
        "label": "Início",
        "kind": "DATE",
        "choices": [],
        "required": false
      },
      {
        "key": "endDate",
        "label": "Fim",
        "kind": "DATE",
        "choices": [],
        "required": false
      },
      {
        "key": "occurrenceCount",
        "label": "Ocorrências",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "dayOfMonth",
        "label": "Dia do mês",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "weekdays",
        "label": "Dias da semana • 1,3,5",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": true
  },
  {
    "type": "financial_category",
    "label": "Categorias financeiras",
    "group": "Finanças",
    "fields": [
      {
        "key": "transactionType",
        "label": "Tipo",
        "kind": "CHOICE",
        "choices": [
          "income",
          "expense"
        ],
        "required": false
      },
      {
        "key": "category",
        "label": "Categoria principal",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "subcategory",
        "label": "Subcategoria",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "financial_rule",
    "label": "Regras financeiras",
    "group": "Finanças",
    "fields": [
      {
        "key": "contains",
        "label": "Descrição contém",
        "kind": "TEXT",
        "choices": [],
        "required": true
      },
      {
        "key": "category",
        "label": "Categoria",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "subcategory",
        "label": "Subcategoria",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "account",
        "label": "Conta",
        "kind": "REFERENCE",
        "choices": [],
        "required": false
      },
      {
        "key": "enabled",
        "label": "Ativa",
        "kind": "CHOICE",
        "choices": [
          "yes",
          "no"
        ],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "financial_template",
    "label": "Lançamentos favoritos",
    "group": "Finanças",
    "fields": [
      {
        "key": "amount",
        "label": "Valor",
        "kind": "MONEY",
        "choices": [],
        "required": true
      },
      {
        "key": "category",
        "label": "Categoria",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "subcategory",
        "label": "Subcategoria",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "currency",
        "label": "Moeda ISO • BRL, USD, EUR",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "account",
        "label": "Conta",
        "kind": "REFERENCE",
        "choices": [],
        "required": false
      },
      {
        "key": "card",
        "label": "Cartão",
        "kind": "REFERENCE",
        "choices": [],
        "required": false
      },
      {
        "key": "planned",
        "label": "Previsto",
        "kind": "CHOICE",
        "choices": [
          "Não",
          "Sim"
        ],
        "required": false
      },
      {
        "key": "paymentMethod",
        "label": "Forma de pagamento",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "dueDate",
        "label": "Vencimento",
        "kind": "DATE",
        "choices": [],
        "required": false
      },
      {
        "key": "competence",
        "label": "Competência • AAAA-MM",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "person",
        "label": "Pessoa / empresa",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "costCenter",
        "label": "Centro de custo",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "transactionType",
        "label": "Tipo",
        "kind": "CHOICE",
        "choices": [
          "income",
          "expense"
        ],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "financial_asset",
    "label": "Ativos e patrimônio",
    "group": "Finanças",
    "fields": [
      {
        "key": "amount",
        "label": "Valor de aquisição",
        "kind": "MONEY",
        "choices": [],
        "required": false
      },
      {
        "key": "current",
        "label": "Valor atual",
        "kind": "MONEY",
        "choices": [],
        "required": false
      },
      {
        "key": "assetType",
        "label": "Tipo de ativo",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "currency",
        "label": "Moeda ISO",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "habit",
    "label": "Hábitos",
    "group": "Rotina",
    "fields": [
      {
        "key": "frequency",
        "label": "Frequência",
        "kind": "CHOICE",
        "choices": [
          "Diária",
          "Dias úteis",
          "Fim de semana"
        ],
        "required": false
      },
      {
        "key": "target",
        "label": "Meta diária",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "reminder",
        "label": "Lembrete • HH:mm",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "water",
    "label": "Água",
    "group": "Rotina",
    "fields": [
      {
        "key": "ml",
        "label": "Volume • ml",
        "kind": "DECIMAL",
        "choices": [],
        "required": true
      }
    ],
    "checkable": false
  },
  {
    "type": "health",
    "label": "Saúde e medições",
    "group": "Rotina",
    "fields": [
      {
        "key": "metric",
        "label": "Métrica",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "value",
        "label": "Valor",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "unit",
        "label": "Unidade",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "sleep",
    "label": "Sono",
    "group": "Rotina",
    "fields": [
      {
        "key": "hours",
        "label": "Horas dormidas",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "quality",
        "label": "Qualidade",
        "kind": "CHOICE",
        "choices": [
          "Boa",
          "Regular",
          "Ruim"
        ],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "workout",
    "label": "Treinos",
    "group": "Rotina",
    "fields": [
      {
        "key": "minutes",
        "label": "Duração • min",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "exercise",
        "label": "Exercícios e séries",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "trip",
    "label": "Viagens",
    "group": "Vida pessoal",
    "fields": [
      {
        "key": "destination",
        "label": "Destino",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "endDate",
        "label": "Volta",
        "kind": "DATE",
        "choices": [],
        "required": false
      },
      {
        "key": "amount",
        "label": "Orçamento",
        "kind": "MONEY",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "reservation",
    "label": "Reservas e roteiro",
    "group": "Vida pessoal",
    "fields": [
      {
        "key": "place",
        "label": "Local",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "code",
        "label": "Código da reserva",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "time",
        "label": "Horário",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "packing",
    "label": "Mala e checklists",
    "group": "Vida pessoal",
    "fields": [
      {
        "key": "category",
        "label": "Categoria",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "quantity",
        "label": "Quantidade",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      }
    ],
    "checkable": true
  },
  {
    "type": "contact",
    "label": "Contatos importantes",
    "group": "Vida pessoal",
    "fields": [
      {
        "key": "phone",
        "label": "Telefone",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "email",
        "label": "Email",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "category",
        "label": "Categoria",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "birthday",
    "label": "Aniversários e datas",
    "group": "Vida pessoal",
    "fields": [
      {
        "key": "birth",
        "label": "Data de nascimento",
        "kind": "DATE",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "vehicle",
    "label": "Veículos",
    "group": "Casa e veículo",
    "fields": [
      {
        "key": "plate",
        "label": "Placa",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "model",
        "label": "Modelo",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "year",
        "label": "Ano",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "fuel",
    "label": "Abastecimentos",
    "group": "Casa e veículo",
    "fields": [
      {
        "key": "liters",
        "label": "Litros",
        "kind": "DECIMAL",
        "choices": [],
        "required": true
      },
      {
        "key": "amount",
        "label": "Total",
        "kind": "MONEY",
        "choices": [],
        "required": true
      },
      {
        "key": "odometer",
        "label": "Quilometragem",
        "kind": "DECIMAL",
        "choices": [],
        "required": true
      }
    ],
    "checkable": false
  },
  {
    "type": "maintenance",
    "label": "Manutenções",
    "group": "Casa e veículo",
    "fields": [
      {
        "key": "amount",
        "label": "Custo",
        "kind": "MONEY",
        "choices": [],
        "required": false
      },
      {
        "key": "next",
        "label": "Próxima revisão",
        "kind": "DATE",
        "choices": [],
        "required": false
      }
    ],
    "checkable": true
  },
  {
    "type": "shopping",
    "label": "Lista de compras",
    "group": "Casa e veículo",
    "fields": [
      {
        "key": "quantity",
        "label": "Quantidade",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "unit",
        "label": "Unidade",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "amount",
        "label": "Preço unitário",
        "kind": "MONEY",
        "choices": [],
        "required": false
      },
      {
        "key": "category",
        "label": "Categoria",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": true
  },
  {
    "type": "pantry",
    "label": "Despensa",
    "group": "Casa e veículo",
    "fields": [
      {
        "key": "quantity",
        "label": "Quantidade",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "expiry",
        "label": "Validade",
        "kind": "DATE",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "recipe",
    "label": "Receitas culinárias",
    "group": "Casa e veículo",
    "fields": [
      {
        "key": "ingredients",
        "label": "Ingredientes • uma linha por item",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "minutes",
        "label": "Preparo • min",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "servings",
        "label": "Porções",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "asset",
    "label": "Patrimônio e garantias",
    "group": "Casa e veículo",
    "fields": [
      {
        "key": "amount",
        "label": "Valor",
        "kind": "MONEY",
        "choices": [],
        "required": false
      },
      {
        "key": "warranty",
        "label": "Garantia até",
        "kind": "DATE",
        "choices": [],
        "required": false
      },
      {
        "key": "serial",
        "label": "Número de série",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "document",
    "label": "Documentos",
    "group": "Vida pessoal",
    "fields": [
      {
        "key": "expiry",
        "label": "Vencimento",
        "kind": "DATE",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "book",
    "label": "Livros",
    "group": "Biblioteca",
    "fields": [
      {
        "key": "author",
        "label": "Autor",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "pages",
        "label": "Total de páginas",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "progress",
        "label": "Página atual",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "rating",
        "label": "Avaliação • 0 a 5",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "movie",
    "label": "Filmes e séries",
    "group": "Biblioteca",
    "fields": [
      {
        "key": "status",
        "label": "Status",
        "kind": "CHOICE",
        "choices": [
          "Quero assistir",
          "Assistindo",
          "Concluído",
          "Abandonado"
        ],
        "required": false
      },
      {
        "key": "progress",
        "label": "Episódio",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "rating",
        "label": "Avaliação • 0 a 5",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "game",
    "label": "Jogos",
    "group": "Biblioteca",
    "fields": [
      {
        "key": "platform",
        "label": "Plataforma",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "status",
        "label": "Status",
        "kind": "CHOICE",
        "choices": [
          "Backlog",
          "Jogando",
          "Zerado",
          "Abandonado"
        ],
        "required": false
      },
      {
        "key": "hours",
        "label": "Horas jogadas",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "note",
    "label": "Notas",
    "group": "Notas e diário",
    "fields": [
      {
        "key": "folder",
        "label": "Pasta",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "archived",
        "label": "Arquivada",
        "kind": "CHOICE",
        "choices": [
          "Não",
          "Sim"
        ],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "journal",
    "label": "Diário e humor",
    "group": "Notas e diário",
    "fields": [
      {
        "key": "mood",
        "label": "Humor",
        "kind": "CHOICE",
        "choices": [
          "Muito feliz",
          "Feliz",
          "Neutro",
          "Triste",
          "Muito triste"
        ],
        "required": false
      },
      {
        "key": "gratitude",
        "label": "Gratidão",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "link",
    "label": "Links e favoritos",
    "group": "Notas e diário",
    "fields": [
      {
        "key": "url",
        "label": "URL",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "task",
    "label": "Tarefas",
    "group": "Produtividade",
    "fields": [
      {
        "key": "priority",
        "label": "Prioridade",
        "kind": "CHOICE",
        "choices": [
          "Baixa",
          "Média",
          "Alta",
          "Urgente"
        ],
        "required": false
      },
      {
        "key": "status",
        "label": "Etapa",
        "kind": "CHOICE",
        "choices": [
          "A fazer",
          "Fazendo",
          "Concluído"
        ],
        "required": false
      },
      {
        "key": "recurrence",
        "label": "Repetir",
        "kind": "CHOICE",
        "choices": [
          "Nunca",
          "Diária",
          "Semanal",
          "Mensal"
        ],
        "required": false
      },
      {
        "key": "reminder",
        "label": "Lembrete • HH:mm",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": true
  },
  {
    "type": "project",
    "label": "Projetos",
    "group": "Produtividade",
    "fields": [
      {
        "key": "deadline",
        "label": "Prazo",
        "kind": "DATE",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "event",
    "label": "Calendário",
    "group": "Produtividade",
    "fields": [
      {
        "key": "time",
        "label": "Horário • HH:mm",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "end",
        "label": "Fim • HH:mm",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "place",
        "label": "Local",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "reminder",
        "label": "Lembrete • HH:mm",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "goal",
    "label": "Metas",
    "group": "Produtividade",
    "fields": [
      {
        "key": "target",
        "label": "Objetivo numérico",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "progress",
        "label": "Progresso",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "deadline",
        "label": "Prazo",
        "kind": "DATE",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "plan",
    "label": "Planejamento",
    "group": "Produtividade",
    "fields": [
      {
        "key": "period",
        "label": "Período",
        "kind": "CHOICE",
        "choices": [
          "Dia",
          "Semana",
          "Mês",
          "Trimestre",
          "Ano"
        ],
        "required": false
      },
      {
        "key": "review",
        "label": "Revisão e aprendizado",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "inbox",
    "label": "Inbox",
    "group": "Produtividade",
    "fields": [
      {
        "key": "read",
        "label": "Lido",
        "kind": "CHOICE",
        "choices": [
          "Não",
          "Sim"
        ],
        "required": false
      }
    ],
    "checkable": true
  },
  {
    "type": "subject",
    "label": "Disciplinas",
    "group": "Estudos",
    "fields": [
      {
        "key": "teacher",
        "label": "Professor",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "schedule",
        "label": "Horários",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "minimum",
        "label": "Média necessária",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "exam",
    "label": "Provas e trabalhos",
    "group": "Estudos",
    "fields": [
      {
        "key": "time",
        "label": "Horário",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "weight",
        "label": "Peso",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "grade",
        "label": "Nota",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      }
    ],
    "checkable": true
  },
  {
    "type": "grade",
    "label": "Notas escolares",
    "group": "Estudos",
    "fields": [
      {
        "key": "grade",
        "label": "Nota",
        "kind": "DECIMAL",
        "choices": [],
        "required": true
      },
      {
        "key": "weight",
        "label": "Peso",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "attendance",
    "label": "Frequência",
    "group": "Estudos",
    "fields": [
      {
        "key": "present",
        "label": "Presente",
        "kind": "CHOICE",
        "choices": [
          "Sim",
          "Não"
        ],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "flashcard",
    "label": "Flashcards",
    "group": "Estudos",
    "fields": [
      {
        "key": "answer",
        "label": "Resposta",
        "kind": "TEXT",
        "choices": [],
        "required": true
      },
      {
        "key": "interval",
        "label": "Intervalo • dias",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "focus",
    "label": "Sessões de foco",
    "group": "Estudos",
    "fields": [
      {
        "key": "minutes",
        "label": "Minutos",
        "kind": "DECIMAL",
        "choices": [],
        "required": true
      }
    ],
    "checkable": false
  },
  {
    "type": "routine",
    "label": "Central de rotina",
    "group": "Seu dia",
    "fields": [],
    "checkable": false
  },
  {
    "type": "checklist",
    "label": "Checklists rápidos",
    "group": "Seu dia",
    "fields": [
      {
        "key": "list",
        "label": "Nome da lista",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "priority",
        "label": "Prioridade",
        "kind": "CHOICE",
        "choices": [
          "Baixa",
          "Média",
          "Alta"
        ],
        "required": false
      }
    ],
    "checkable": true
  },
  {
    "type": "countdown",
    "label": "Contagem regressiva",
    "group": "Seu dia",
    "fields": [
      {
        "key": "category",
        "label": "Ocasião",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "meal",
    "label": "Cardápio semanal",
    "group": "Casa e veículo",
    "fields": [
      {
        "key": "ingredients",
        "label": "Ingredientes • uma linha por item",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "minutes",
        "label": "Preparo em minutos",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      }
    ],
    "checkable": true
  },
  {
    "type": "chore",
    "label": "Rotina da casa",
    "group": "Casa e veículo",
    "fields": [
      {
        "key": "room",
        "label": "Cômodo",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "person",
        "label": "Responsável",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": true
  },
  {
    "type": "pet",
    "label": "Cuidados dos pets",
    "group": "Casa e veículo",
    "fields": [
      {
        "key": "pet",
        "label": "Nome do pet",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "time",
        "label": "Horário",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": true
  },
  {
    "type": "medicine",
    "label": "Medicamentos",
    "group": "Bem-estar",
    "fields": [
      {
        "key": "dose",
        "label": "Dose prescrita",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "time",
        "label": "Horário",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "stock",
        "label": "Quantidade em estoque",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      }
    ],
    "checkable": true
  },
  {
    "type": "appointment",
    "label": "Consultas e cuidados",
    "group": "Bem-estar",
    "fields": [
      {
        "key": "place",
        "label": "Local / profissional",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "time",
        "label": "Horário",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "phone",
        "label": "Telefone",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": true
  },
  {
    "type": "mood",
    "label": "Humor e energia",
    "group": "Bem-estar",
    "fields": [
      {
        "key": "reason",
        "label": "O que influenciou seu dia?",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "measurement",
    "label": "Medidas corporais",
    "group": "Bem-estar",
    "fields": [
      {
        "key": "weight",
        "label": "Peso em kg",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "waist",
        "label": "Cintura em cm",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "height",
        "label": "Altura em cm",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "exercise",
    "label": "Séries de exercícios",
    "group": "Bem-estar",
    "fields": [
      {
        "key": "sets",
        "label": "Séries",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "reps",
        "label": "Repetições",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "load",
        "label": "Carga em kg",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "minutes",
        "label": "Duração em minutos",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      }
    ],
    "checkable": true
  },
  {
    "type": "savings_goal",
    "label": "Metas financeiras",
    "group": "Finanças",
    "fields": [
      {
        "key": "amount",
        "label": "Meta em R$",
        "kind": "MONEY",
        "choices": [],
        "required": true
      },
      {
        "key": "saved",
        "label": "Já reservado em R$",
        "kind": "MONEY",
        "choices": [],
        "required": false
      },
      {
        "key": "account",
        "label": "Conta",
        "kind": "REFERENCE",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "debt",
    "label": "Dívidas e empréstimos",
    "group": "Finanças",
    "fields": [
      {
        "key": "person",
        "label": "Pessoa / instituição",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "amount",
        "label": "Valor total",
        "kind": "MONEY",
        "choices": [],
        "required": true
      },
      {
        "key": "paid",
        "label": "Valor quitado",
        "kind": "MONEY",
        "choices": [],
        "required": false
      }
    ],
    "checkable": true
  },
  {
    "type": "bill",
    "label": "Contas a pagar",
    "group": "Finanças",
    "fields": [
      {
        "key": "amount",
        "label": "Valor",
        "kind": "MONEY",
        "choices": [],
        "required": true
      },
      {
        "key": "category",
        "label": "Categoria",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "account",
        "label": "Conta",
        "kind": "REFERENCE",
        "choices": [],
        "required": false
      }
    ],
    "checkable": true
  },
  {
    "type": "wishlist",
    "label": "Lista de desejos",
    "group": "Vida pessoal",
    "fields": [
      {
        "key": "amount",
        "label": "Preço estimado",
        "kind": "MONEY",
        "choices": [],
        "required": false
      },
      {
        "key": "url",
        "label": "Link",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": true
  },
  {
    "type": "gift",
    "label": "Presentes",
    "group": "Vida pessoal",
    "fields": [
      {
        "key": "person",
        "label": "Para quem",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "amount",
        "label": "Orçamento",
        "kind": "MONEY",
        "choices": [],
        "required": false
      },
      {
        "key": "occasion",
        "label": "Ocasião",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": true
  },
  {
    "type": "course",
    "label": "Cursos e aprendizado",
    "group": "Estudos",
    "fields": [
      {
        "key": "platform",
        "label": "Plataforma",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "lessons",
        "label": "Total de aulas",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "completed",
        "label": "Aulas concluídas",
        "kind": "DECIMAL",
        "choices": [],
        "required": false
      },
      {
        "key": "url",
        "label": "Link",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "job",
    "label": "Vagas e candidaturas",
    "group": "Vida pessoal",
    "fields": [
      {
        "key": "company",
        "label": "Empresa",
        "kind": "TEXT",
        "choices": [],
        "required": false
      },
      {
        "key": "url",
        "label": "Link",
        "kind": "TEXT",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "subscription_audit",
    "label": "Revisão de serviços",
    "group": "Finanças",
    "fields": [
      {
        "key": "amount",
        "label": "Custo mensal",
        "kind": "MONEY",
        "choices": [],
        "required": false
      },
      {
        "key": "renewal",
        "label": "Próxima renovação",
        "kind": "DATE",
        "choices": [],
        "required": false
      }
    ],
    "checkable": false
  },
  {
    "type": "sketch",
    "label": "Desenho e esboços",
    "group": "Criatividade",
    "fields": [],
    "checkable": false
  },
  {
    "type": "scanner",
    "label": "Leitor de QR e códigos",
    "group": "Ferramentas",
    "fields": [],
    "checkable": false
  },
  {
    "type": "decisions",
    "label": "Sorteios, equipes e dados",
    "group": "Ferramentas",
    "fields": [],
    "checkable": false
  },
  {
    "type": "playroom",
    "label": "Jogos rápidos",
    "group": "Lazer",
    "fields": [],
    "checkable": false
  },
  {
    "type": "worldclock",
    "label": "Relógio mundial",
    "group": "Ferramentas",
    "fields": [],
    "checkable": false
  },
  {
    "type": "city",
    "label": "Clima",
    "group": "Clima",
    "fields": [],
    "checkable": false
  },
  {
    "type": "tools",
    "label": "Ferramentas",
    "group": "Ferramentas",
    "fields": [],
    "checkable": false
  },
  {
    "type": "assistant",
    "label": "Assistente",
    "group": "Sistema",
    "fields": [],
    "checkable": false
  },
  {
    "type": "checkin",
    "label": "Registros de hábitos",
    "group": "Rotina",
    "fields": [],
    "checkable": false
  },
  {
    "type": "focus_session",
    "label": "Sessões de foco",
    "group": "Estudos",
    "fields": [],
    "checkable": false
  }
] as const;
