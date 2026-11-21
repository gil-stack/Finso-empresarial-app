// Modelos de la API móvil de Finso Empresas (`/movil/v1`).
// Cada uno se arma desde el JSON del contrato:
// /mnt/project-files/app-empresarial/contrato-api-movil.md
// La API simulada también pasa por estos `desdeJson`, así que si el contrato
// cambia, las pruebas lo notan.

typedef Json = Map<String, dynamic>;

DateTime _fecha(Object? v) => DateTime.parse(v as String).toUtc();
DateTime? _fechaOpcional(Object? v) => v == null ? null : _fecha(v);

// ───────────────────────── Entrar ─────────────────────────

enum PasoEntrada {
  capturarCelular('CAPTURAR_CELULAR'),
  codigoSms('CODIGO_SMS'),
  codigoCorreo('CODIGO_CORREO');

  const PasoEntrada(this.clave);
  final String clave;

  static PasoEntrada desde(String clave) =>
      values.firstWhere((p) => p.clave == clave);
}

/// Respuesta de `POST /sesion/inicio`: lo que falta para registrar el teléfono.
class RetoDeEntrada {
  const RetoDeEntrada({
    required this.reto,
    required this.expiraEn,
    required this.pasos,
    this.celularEnmascarado,
    this.correoEnmascarado,
    this.reenvioDisponibleEn,
  });

  final String reto;
  final DateTime expiraEn;
  final List<PasoEntrada> pasos;
  final String? celularEnmascarado;
  final String? correoEnmascarado;
  final DateTime? reenvioDisponibleEn;

  factory RetoDeEntrada.desdeJson(Json j) => RetoDeEntrada(
    reto: j['reto'] as String,
    expiraEn: _fecha(j['expiraEn']),
    pasos: [for (final p in j['pasos'] as List) PasoEntrada.desde(p as String)],
    celularEnmascarado: j['celularEnmascarado'] as String?,
    correoEnmascarado: j['correoEnmascarado'] as String?,
    reenvioDisponibleEn: _fechaOpcional(j['reenvioDisponibleEn']),
  );

  RetoDeEntrada conPasos(
    List<PasoEntrada> pendientes, {
    String? celularEnmascarado,
    DateTime? reenvioDisponibleEn,
  }) => RetoDeEntrada(
    reto: reto,
    expiraEn: expiraEn,
    pasos: pendientes,
    celularEnmascarado: celularEnmascarado ?? this.celularEnmascarado,
    correoEnmascarado: correoEnmascarado,
    reenvioDisponibleEn: reenvioDisponibleEn ?? this.reenvioDisponibleEn,
  );
}

class Sesion {
  const Sesion({
    required this.acceso,
    required this.accesoExpiraEn,
    required this.renovacion,
    required this.renovacionExpiraEn,
  });

  final String acceso;
  final DateTime accesoExpiraEn;
  final String renovacion;
  final DateTime renovacionExpiraEn;

  factory Sesion.desdeJson(Json j) => Sesion(
    acceso: j['acceso'] as String,
    accesoExpiraEn: _fecha(j['accesoExpiraEn']),
    renovacion: j['renovacion'] as String,
    renovacionExpiraEn: _fecha(j['renovacionExpiraEn']),
  );

  Json aJson() => {
    'acceso': acceso,
    'accesoExpiraEn': accesoExpiraEn.toIso8601String(),
    'renovacion': renovacion,
    'renovacionExpiraEn': renovacionExpiraEn.toIso8601String(),
  };
}

enum Papel { administrador, solicitante, liberador, consulta, otro }

Papel _papel(String s) => switch (s) {
  'ADMINISTRADOR' => Papel.administrador,
  'SOLICITANTE' => Papel.solicitante,
  'LIBERADOR' => Papel.liberador,
  'CONSULTA' => Papel.consulta,
  _ => Papel.otro,
};

class Usuario {
  const Usuario({
    required this.id,
    required this.nombre,
    required this.correo,
    required this.papeles,
    required this.empresaId,
    required this.empresaNombre,
    required this.operacionDelDueno,
  });

  final String id;
  final String nombre;
  final String correo;
  final Set<Papel> papeles;
  final String empresaId;
  final String empresaNombre;
  final bool operacionDelDueno;

  String get nombreCorto => nombre.split(' ').first;
  bool get puedePedirSpei =>
      papeles.contains(Papel.solicitante) ||
      papeles.contains(Papel.administrador);
  bool get puedeLiberar =>
      papeles.contains(Papel.liberador) ||
      papeles.contains(Papel.administrador);

  factory Usuario.desdeJson(Json j) {
    final empresa = j['empresa'] as Json;
    return Usuario(
      id: j['id'] as String,
      nombre: j['nombre'] as String,
      correo: j['correo'] as String,
      papeles: {for (final p in j['papeles'] as List) _papel(p as String)},
      empresaId: empresa['id'] as String,
      empresaNombre: empresa['nombre'] as String,
      operacionDelDueno: j['operacionDelDueno'] as bool? ?? false,
    );
  }
}

/// Respuesta de `POST /dispositivos` y de entrar con biometría.
class Ingreso {
  const Ingreso({
    required this.sesion,
    required this.usuario,
    this.dispositivo,
  });
  final Sesion sesion;
  final Usuario usuario;

  /// Solo al registrar el teléfono.
  final String? dispositivo;

  factory Ingreso.desdeJson(Json j) => Ingreso(
    sesion: Sesion.desdeJson(j['sesion'] as Json),
    usuario: Usuario.desdeJson(j['usuario'] as Json),
    dispositivo: j['dispositivo'] as String?,
  );
}

class RetoDeFirma {
  const RetoDeFirma({required this.reto, required this.expiraEn});
  final String reto;
  final DateTime expiraEn;

  factory RetoDeFirma.desdeJson(Json j) =>
      RetoDeFirma(reto: j['reto'] as String, expiraEn: _fecha(j['expiraEn']));
}

// ───────────────────────── Cuenta ─────────────────────────

class Cuenta {
  const Cuenta({
    required this.clabe,
    required this.titular,
    required this.bloqueada,
    required this.saldoCentavos,
    required this.disponibleCentavos,
    required this.horaDelSaldo,
  });

  final String clabe;
  final String titular;
  final bool bloqueada;
  final int saldoCentavos;
  final int disponibleCentavos;
  final DateTime horaDelSaldo;

  /// `6461 8053 5000 0012 34`: la CLABE en grupos de 4 para leerla y dictarla.
  String get clabeAgrupada =>
      clabe.replaceAllMapped(RegExp(r'.{1,4}'), (m) => '${m[0]} ').trimRight();

  factory Cuenta.desdeJson(Json j) => Cuenta(
    clabe: j['clabe'] as String,
    titular: j['titular'] as String,
    bloqueada: j['estado'] == 'BLOQUEADA',
    saldoCentavos: j['saldoCentavos'] as int,
    disponibleCentavos: j['disponibleCentavos'] as int,
    horaDelSaldo: _fecha(j['horaDelSaldo']),
  );
}

class LecturaDeCuenta {
  const LecturaDeCuenta({required this.cuenta, required this.sinConexion});

  /// null: la cuenta todavía se está abriendo.
  final Cuenta? cuenta;

  /// El core no contestó: es la última copia guardada.
  final bool sinConexion;

  factory LecturaDeCuenta.desdeJson(Json j) => LecturaDeCuenta(
    cuenta: j['cuenta'] == null ? null : Cuenta.desdeJson(j['cuenta'] as Json),
    sinConexion: j['sinConexion'] as bool? ?? false,
  );
}

enum TipoMovimiento { speiEntrada, speiSalida, comision, devolucion, ajuste }

TipoMovimiento _tipoMovimiento(String s) => switch (s) {
  'SPEI_ENTRADA' => TipoMovimiento.speiEntrada,
  'SPEI_SALIDA' => TipoMovimiento.speiSalida,
  'COMISION' => TipoMovimiento.comision,
  'DEVOLUCION' => TipoMovimiento.devolucion,
  _ => TipoMovimiento.ajuste,
};

class Contraparte {
  const Contraparte({required this.nombre, this.banco, this.clabeFinal});
  final String nombre;
  final String? banco;
  final String? clabeFinal;

  factory Contraparte.desdeJson(Json j) => Contraparte(
    nombre: j['nombre'] as String,
    banco: j['banco'] as String?,
    clabeFinal: j['clabeFinal'] as String?,
  );
}

class Movimiento {
  const Movimiento({
    required this.id,
    required this.abono,
    required this.tipo,
    required this.importeCentavos,
    required this.saldoResultanteCentavos,
    required this.fecha,
    this.concepto,
    this.claveRastreo,
    this.contraparte,
    this.orden,
    this.comprobante = false,
  });

  final String id;

  /// true = entra dinero (ABONO); false = sale (CARGO).
  final bool abono;
  final TipoMovimiento tipo;
  final int importeCentavos;
  final int saldoResultanteCentavos;
  final DateTime fecha;
  final String? concepto;
  final String? claveRastreo;
  final Contraparte? contraparte;
  final String? orden;
  final bool comprobante;

  /// El importe con su signo: positivo si entra, negativo si sale.
  int get importeConSigno => abono ? importeCentavos : -importeCentavos;

  String get titulo =>
      contraparte?.nombre ??
      switch (tipo) {
        TipoMovimiento.comision => 'Comisión',
        TipoMovimiento.devolucion => 'Devolución',
        TipoMovimiento.ajuste => 'Ajuste',
        TipoMovimiento.speiEntrada => 'SPEI recibido',
        TipoMovimiento.speiSalida => 'SPEI enviado',
      };

  factory Movimiento.desdeJson(Json j) => Movimiento(
    id: j['id'] as String,
    abono: j['sentido'] == 'ABONO',
    tipo: _tipoMovimiento(j['tipo'] as String),
    importeCentavos: j['importeCentavos'] as int,
    saldoResultanteCentavos: j['saldoResultanteCentavos'] as int,
    fecha: _fecha(j['fecha']),
    concepto: j['concepto'] as String?,
    claveRastreo: j['claveRastreo'] as String?,
    contraparte: j['contraparte'] == null
        ? null
        : Contraparte.desdeJson(j['contraparte'] as Json),
    orden: j['orden'] as String?,
    comprobante: j['comprobante'] as bool? ?? false,
  );
}

class PaginaDeMovimientos {
  const PaginaDeMovimientos({
    required this.mes,
    required this.elementos,
    this.siguienteCursor,
    this.incompleto = false,
    this.sinConexion = false,
  });

  final String mes;
  final List<Movimiento> elementos;
  final String? siguienteCursor;
  final bool incompleto;
  final bool sinConexion;

  factory PaginaDeMovimientos.desdeJson(Json j) => PaginaDeMovimientos(
    mes: j['mes'] as String,
    elementos: [
      for (final m in j['elementos'] as List) Movimiento.desdeJson(m as Json),
    ],
    siguienteCursor: j['siguienteCursor'] as String?,
    incompleto: j['incompleto'] as bool? ?? false,
    sinConexion: j['sinConexion'] as bool? ?? false,
  );
}

// ───────────────────────── Destinos ─────────────────────────

class Destino {
  const Destino({
    required this.id,
    required this.titular,
    required this.proveedor,
    required this.clabeFinal,
    required this.activo,
    required this.activoDesde,
    this.banco,
  });

  final String id;
  final String titular;

  /// true = PROVEEDOR; false = COLABORADOR.
  final bool proveedor;
  final String? banco;
  final String clabeFinal;

  /// false = EN_ESPERA (registrado hace menos de 30 min): todavía no se le paga.
  final bool activo;
  final DateTime activoDesde;

  /// Ya se le puede pagar: está ACTIVO o ya pasó su hora de espera.
  bool listoEn(DateTime ahora) => activo || !ahora.isBefore(activoDesde);

  String get detalle => [?banco, '•••• $clabeFinal'].join(' · ');

  factory Destino.desdeJson(Json j) => Destino(
    id: j['id'] as String,
    titular: j['titular'] as String,
    proveedor: j['tipo'] == 'PROVEEDOR',
    banco: j['banco'] as String?,
    clabeFinal: j['clabeFinal'] as String,
    activo: j['estado'] == 'ACTIVO',
    activoDesde: _fecha(j['activoDesde']),
  );
}

// ───────────────────────── SPEI ─────────────────────────

/// Lo que se va a mandar, tal cual lo tecleó la persona.
class SolicitudSpei {
  const SolicitudSpei({
    required this.destino,
    required this.importeCentavos,
    required this.concepto,
    required this.referenciaNumerica,
  });

  final String destino;
  final int importeCentavos;
  final String concepto;
  final int referenciaNumerica;

  Json aJson() => {
    'destino': destino,
    'importeCentavos': importeCentavos,
    'concepto': concepto,
    'referenciaNumerica': referenciaNumerica,
  };

  /// El concepto admite 1–40 caracteres de `[A-Za-z0-9 .,&-]` (igual que el core).
  static final patronConcepto = RegExp(r'^[A-Za-z0-9 .,&-]{1,40}$');
}

class PreparacionSpei {
  const PreparacionSpei({
    required this.reto,
    required this.expiraEn,
    required this.titular,
    required this.clabeFinal,
    required this.importeCentavos,
    required this.comisionCentavos,
    required this.concepto,
    required this.referenciaNumerica,
    required this.requiereLiberador,
    this.banco,
  });

  final String reto;
  final DateTime expiraEn;
  final String titular;
  final String? banco;
  final String clabeFinal;
  final int importeCentavos;
  final int comisionCentavos;
  final String concepto;
  final int referenciaNumerica;

  /// true = después de pedirlo, otra persona tiene que liberarlo.
  final bool requiereLiberador;

  factory PreparacionSpei.desdeJson(Json j) {
    final r = j['resumen'] as Json;
    final d = r['destino'] as Json;
    return PreparacionSpei(
      reto: j['reto'] as String,
      expiraEn: _fecha(j['expiraEn']),
      titular: d['titular'] as String,
      banco: d['banco'] as String?,
      clabeFinal: d['clabeFinal'] as String,
      importeCentavos: r['importeCentavos'] as int,
      comisionCentavos: r['comisionCentavos'] as int? ?? 0,
      concepto: r['concepto'] as String,
      referenciaNumerica: r['referenciaNumerica'] as int,
      requiereLiberador: j['requiereLiberador'] as bool,
    );
  }
}

enum EstadoOrden {
  porLiberar('POR_LIBERAR', 'Por liberar'),
  enProceso('EN_PROCESO', 'En proceso'),
  enviada('ENVIADA', 'Enviada'),
  liquidada('LIQUIDADA', 'Liquidada'),
  devuelta('DEVUELTA', 'Devuelta'),
  rechazada('RECHAZADA', 'Rechazada'),
  cancelada('CANCELADA', 'Cancelada');

  const EstadoOrden(this.clave, this.etiqueta);
  final String clave;
  final String etiqueta;

  bool get final_ =>
      this == liquidada ||
      this == devuelta ||
      this == rechazada ||
      this == cancelada;
  bool get exitosa => this == enviada || this == liquidada;

  static EstadoOrden desde(String clave) =>
      values.firstWhere((e) => e.clave == clave);
}

class Orden {
  const Orden({
    required this.id,
    required this.estado,
    required this.destinoId,
    required this.titular,
    required this.clabeFinal,
    required this.importeCentavos,
    required this.concepto,
    required this.referenciaNumerica,
    required this.pedidaPor,
    required this.creadaEn,
    required this.actualizadaEn,
    this.banco,
    this.claveRastreo,
    this.liberadaPor,
    this.motivo,
  });

  final String id;
  final EstadoOrden estado;
  final String destinoId;
  final String titular;
  final String? banco;
  final String clabeFinal;
  final int importeCentavos;
  final String concepto;
  final int referenciaNumerica;
  final String? claveRastreo;
  final String pedidaPor;
  final String? liberadaPor;
  final DateTime creadaEn;
  final DateTime actualizadaEn;
  final String? motivo;

  factory Orden.desdeJson(Json j) {
    final d = j['destino'] as Json;
    return Orden(
      id: j['id'] as String,
      estado: EstadoOrden.desde(j['estado'] as String),
      destinoId: d['id'] as String,
      titular: d['titular'] as String,
      banco: d['banco'] as String?,
      clabeFinal: d['clabeFinal'] as String,
      importeCentavos: j['importeCentavos'] as int,
      concepto: j['concepto'] as String,
      referenciaNumerica: j['referenciaNumerica'] as int,
      claveRastreo: j['claveRastreo'] as String?,
      pedidaPor: j['pedidaPor'] as String,
      liberadaPor: j['liberadaPor'] as String?,
      creadaEn: _fecha(j['creadaEn']),
      actualizadaEn: _fecha(j['actualizadaEn']),
      motivo: j['motivo'] as String?,
    );
  }
}
