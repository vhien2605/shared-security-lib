package vdt.mini.shared_lib.document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccessRuleDTO {
    private String type;
    private String valueType;
    private String value;
    private Boolean temporary;
    private String expiresAt;
}
