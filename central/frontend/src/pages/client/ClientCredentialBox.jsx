export default function ClientCredentialBox({ credential }) {
  const entries = Array.isArray(credential)
    ? credential.flatMap((item, index) =>
        Object.entries(item || {}).map(([key, value]) => [
          `${index + 1}.${key}`,
          value,
        ]),
      )
    : Object.entries(credential || {});

  if (entries.length === 0) return null;

  return (
    <section className="client-credential-box">
      <h4>Credential trả về một lần</h4>
      <p className="client-credential-box__warning">
        Vui lòng sao chép và lưu an toàn ngay bây giờ. Giá trị này sẽ không
        được hiển thị lại sau khi đóng màn hình.
      </p>
      {entries.map(([key, value]) => (
        <p key={key}>
          <span>{key}</span>
          <code>{String(value)}</code>
        </p>
      ))}
    </section>
  );
}
