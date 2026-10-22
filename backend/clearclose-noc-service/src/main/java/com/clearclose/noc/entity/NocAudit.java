
package com.clearclose.noc.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "NOC_AUDIT")
public class NocAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AUDIT_ID")
    private Long auditId;

    @Column(name = "NOC_ID", nullable = false)
    private Long nocId;

    @Column(name = "ACTION", nullable = false, length = 100)
    private String action;

    @Column(name = "ACTOR_ID")
    private Long actorId;

    @Column(name = "ACTION_AT", nullable = false)
    private LocalDateTime actionAt;

    @Column(name = "REMARKS", length = 1000)
    private String remarks;

    public NocAudit() {}

    public NocAudit(Long nocId, String action,
                    Long actorId, String remarks) {
        this.nocId = nocId;
        this.action = action;
        this.actorId = actorId;
        this.remarks = remarks;
        this.actionAt = LocalDateTime.now();
    }

    public Long getAuditId() { return auditId; }
    public Long getNocId() { return nocId; }
    public String getAction() { return action; }
    public Long getActorId() { return actorId; }
    public LocalDateTime getActionAt() { return actionAt; }
    public String getRemarks() { return remarks; }
}
